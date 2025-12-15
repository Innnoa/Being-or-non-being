#include "network/tcp/tcp_server.hpp"
#include "game/managers/room_manager.hpp"
#include <spdlog/spdlog.h>
#include <arpa/inet.h>
#include <chrono>
#include <cstring>
#include <iostream>

namespace {
constexpr std::size_t kMaxPacketSize = 64 * 1024; // 设定最大包大小
}

std::atomic<uint32_t> TcpSession::next_player_id_{1};

TcpSession::TcpSession(tcp::socket socket) : socket_(std::move(socket)) { //构造函数
}

void TcpSession::start() { 
  read_header();
}

void TcpSession::SendProto(lawnmower::MessageType type, const google::protobuf::Message& message) {
  if (closed_) {
    return;
  }
  lawnmower::Packet packet;
  packet.set_msg_type(type);
  packet.set_payload(message.SerializeAsString());
  send_packet(packet);
}

void TcpSession::handle_disconnect() {
  if (closed_) {
    return;
  }
  closed_ = true;

  if (player_id_ != 0) {
    RoomManager::Instance().RemovePlayer(player_id_);
  }

  asio::error_code ignored_ec;
  socket_.shutdown(tcp::socket::shutdown_both, ignored_ec);
  socket_.close(ignored_ec);
}

void TcpSession::read_header() {
  auto self = shared_from_this(); // 获取自身的shared_ptr
  static int tip = 1;
  asio::async_read(socket_, asio::buffer(length_buffer_), // 先读packet长度
    [this, self](const asio::error_code& ec, std::size_t) {
      if (ec) {
        spdlog::warn("Failed to read packet length: {}", ec.message());
        handle_disconnect();
        return;
      }

      uint32_t net_len = 0;
      std::memcpy(&net_len, length_buffer_.data(), sizeof(net_len));
      const uint32_t body_len = ntohl(net_len);
      std::cout<<"body_len: "<<body_len<<"\n";
      if (body_len == 0 || body_len > kMaxPacketSize) {
        spdlog::warn("Invalid packet length: {}", body_len);
        handle_disconnect();
        return;
      }

      read_buffer_.resize(body_len);
      std::cout<<"finish: "<<tip<<"\n";
      ++tip;
      read_body(body_len);
    }
  );
}

void TcpSession::read_body(std::size_t length) {
  auto self = shared_from_this(); // 获取自身的shared_ptr
  asio::async_read(socket_, asio::buffer(read_buffer_, length), // 然后读具体报文
    [this, self](const asio::error_code& ec, std::size_t) {
      if (ec) {
        spdlog::warn("Failed to read packet body: {}", ec.message());
        handle_disconnect();
        return;
      }

      lawnmower::Packet packet;
      if (!packet.ParseFromArray(read_buffer_.data(), static_cast<int>(read_buffer_.size()))) {
        spdlog::warn("Failed to parse protobuf Packet ({} bytes)", read_buffer_.size());
        read_header();
        return;
      }

      handle_packet(packet);
      if (closed_ || !socket_.is_open()) { // 已经主动断开，不再继续读
        return;
      }
      read_header();
    }
  );
}

void TcpSession::do_write() {
  auto self = shared_from_this(); // 获取自身的shared_ptr
  asio::async_write(socket_, asio::buffer(write_queue_.front()), // 注册异步写入回调
    [this, self](const asio::error_code& ec, std::size_t) {
      if (ec) {
        spdlog::warn("Failed to write packet: {}", ec.message());
        handle_disconnect();
        return;
      }
      write_queue_.pop_front();
      if (!write_queue_.empty()) {
        do_write();
      }
    }
  );
}

void TcpSession::handle_packet(const lawnmower::Packet& packet){
  using lawnmower::MessageType;

  switch (packet.msg_type()){
    case MessageType::MSG_C2S_LOGIN: { // login
      lawnmower::C2S_Login login;
      if (!login.ParseFromString(packet.payload())) {
        spdlog::warn("Failed to parse login payload");
        break;
      }

      if (player_id_ != 0) {
        lawnmower::S2C_LoginResult result;
        result.set_success(false);
        result.set_player_id(player_id_);
        result.set_message_login("重复登录");
        SendProto(MessageType::MSG_S2C_LOGIN_RESULT, result);
        break;
      }

      player_id_ = next_player_id_.fetch_add(1);
      player_name_ = login.player_name().empty() ? ("玩家" + std::to_string(player_id_)) : login.player_name();

      lawnmower::S2C_LoginResult result;
      result.set_success(true);
      result.set_player_id(player_id_);
      result.set_message_login("login success");

      SendProto(MessageType::MSG_S2C_LOGIN_RESULT, result);
      spdlog::info("player login: {} (id={})", player_name_, player_id_);
      break;
    }
    case MessageType::MSG_C2S_HEARTBEAT: { // heartbeat echo with server info
      lawnmower::C2S_Heartbeat heartbeat;
      if (!heartbeat.ParseFromString(packet.payload())) {
        spdlog::warn("Failed to parse heartbeat payload");
        break;
      }

      lawnmower::S2C_Heartbeat reply;
      const auto now_ms = static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::milliseconds>(
          std::chrono::system_clock::now().time_since_epoch()).count());
      reply.set_timestamp(now_ms);
      reply.set_online_players(1);

      SendProto(MessageType::MSG_S2C_HEARTBEAT, reply);
      break;
    }
    case MessageType::MSG_C2S_CREATE_ROOM: {
      lawnmower::C2S_CreateRoom request;
      if (!request.ParseFromString(packet.payload())) {
        spdlog::warn("Failed to parse create room payload");
        break;
      }

      lawnmower::S2C_CreateRoomResult result;
      if (player_id_ == 0) {
        result.set_success(false);
        result.set_message_create("请先登录");
      } else {
        result = RoomManager::Instance().CreateRoom(player_id_, player_name_, weak_from_this(), request);
      }
      SendProto(MessageType::MSG_S2C_CREATE_ROOM_RESULT, result);
      break;
    }
    case MessageType::MSG_C2S_JOIN_ROOM: {
      lawnmower::C2S_JoinRoom request;
      if (!request.ParseFromString(packet.payload())) {
        spdlog::warn("Failed to parse join room payload");
        break;
      }

      lawnmower::S2C_JoinRoomResult result;
      if (player_id_ == 0) {
        result.set_success(false);
        result.set_message_join("请先登录");
      } else {
        result = RoomManager::Instance().JoinRoom(player_id_, player_name_, weak_from_this(), request);
      }
      SendProto(MessageType::MSG_S2C_JOIN_ROOM_RESULT, result);
      break;
    }
    case MessageType::MSG_C2S_LEAVE_ROOM: {
      lawnmower::C2S_LeaveRoom request;
      if (!request.ParseFromString(packet.payload())) {
        spdlog::warn("Failed to parse leave room payload");
        break;
      }

      lawnmower::S2C_LeaveRoomResult result;
      if (player_id_ == 0) {
        result.set_success(false);
        result.set_message_leave("请先登录");
      } else {
        result = RoomManager::Instance().LeaveRoom(player_id_);
      }
      SendProto(MessageType::MSG_S2C_LEAVE_ROOM_RESULT, result);
      break;
    }
    case MessageType::MSG_C2S_REQUEST_QUIT: { // 客户端主动断开连接
      spdlog::info("Client request disconnect");
      handle_disconnect();
      break;
    }
    case MessageType::MSG_UNKNOWN: // 未知类型
    default:
      spdlog::warn("Unhandled message type: {}", static_cast<int>(packet.msg_type()));
  }
}

void TcpSession::send_packet(const lawnmower::Packet& packet){
  const std::string data = packet.SerializeAsString();
  const uint32_t net_len = htonl(static_cast<uint32_t>(data.size()));

  std::string framed;
  framed.resize(sizeof(net_len) + data.size());
  std::memcpy(framed.data(), &net_len, sizeof(net_len));
  std::memcpy(framed.data() + sizeof(net_len), data.data(), data.size());

  const bool write_in_progress = !write_queue_.empty();
  write_queue_.push_back(std::move(framed));
  if (!write_in_progress) {
    do_write();
  }
}

TcpServer::TcpServer(asio::io_context& io, uint16_t port): 
  io_context_(io), // 创建一个上下文
  acceptor_(io_context_, tcp::endpoint(tcp::v4(), port)) { // 监听和接受TCP连接，绑定端口
}

void TcpServer::start() { 
  do_accept(); 
}

void TcpServer::do_accept() {
  acceptor_.async_accept([this](const asio::error_code& ec, tcp::socket socket) { // 异步接受连接，注册回调函数
      // 这里的socket无需手动创建，async_accept会自动创建
    if (!ec) {
        std::make_shared<TcpSession>(std::move(socket))->start(); 
        // 将socket权限转移给Session,对于单个连接由TcpSession单独负责
    } // 立即返回，非阻塞，当有客户端连接时，asio调用回调函数
    do_accept(); // 再次创建一个新的连接，递归
  });
}
