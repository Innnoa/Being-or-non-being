#include "network/tcp/tcp_server.hpp"

TcpSession::TcpSession(tcp::socket socket) : socket_(std::move(socket)) {
}

void TcpSession::start() { 
  do_read(); 
}

void TcpSession::send(const std::string& data) { // 一个接口，暂时没用上
  write_data_ = data;
  do_write();
}

void TcpSession::do_read() {
  auto self = shared_from_this(); // 获取自身的shared_ptr
  socket_.async_read_some(asio::buffer(buffer_), // 注册异步读取回调，读取到的数据保存到buffer
    [this, self](const asio::error_code& ec,std::size_t bytes_transferred) {
      if (ec) {
        return;
      }
    write_data_.assign(buffer_.data(), bytes_transferred); // 将buffer中的数据转移到write_data
    do_write(); // 调用write
    }
  );
}

void TcpSession::do_write() {
  auto self = shared_from_this(); // 获取自身的shared_ptr
  asio::async_write(socket_, asio::buffer(write_data_), //注册异步写入回调
    [this, self](const asio::error_code& ec, std::size_t) {
      if (ec) {
        return;
      }
      do_read(); // 调用read
    }
  );
}

void TcpSession::handle_packet(const lawnmower::Packet& packet){
  switch (packet.msg_type()){
    case 1: { //login
      lawnmower::C2s_login login;
      login.ParseFromString(packet.payload());
      spdlog::info("player login",login.player_name());
      lawnmower::S2c_LoginResult result;
      result.set_success(true);
      result.set_player_id(1001);
      result.set_message("login success");

      lawnmower::Packet reply;
      reply.set_msg_type(2);
      reply_set_payload(result.SerializeAsString());

      send_packet(reply);
      break;
    }
    default:
      spdlog::warn("Unknown message type",packet.msg_type());
  }
}

void send_packet(const Packet& packet){
  std::string data = packet.SerializeAsString();
  uint32_t len = data.size();
}

TcpServer::TcpServer(asio::io_context& io, uint16_t port): 
  io_context_(io), // 创建一个上下文
  acceptor_(io_context_, tcp::endpoint(tcp::v4(), port)) { // 舰艇和接受TCP连接，绑定端口
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
