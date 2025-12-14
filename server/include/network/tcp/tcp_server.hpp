#pragma once

#include <cstdint>
#include <array>
#include <asio.hpp>
#include <deque>
#include <memory>
#include <string>
#include <vector>
#include "../../../generated/message.pb.h"

using asio::ip::tcp;

class TcpSession : public std::enable_shared_from_this<TcpSession> {
 public: 
   explicit TcpSession(tcp::socket socket);
   void start();

 private:
  void read_header();
  void read_body(std::size_t length);
  void do_write();
  void handle_packet(const lawnmower::Packet& packet);
  void send_packet(const lawnmower::Packet& packet);

  tcp::socket socket_;
  std::array<char, sizeof(uint32_t)> length_buffer_{};
  std::vector<char> read_buffer_;
  std::deque<std::string> write_queue_;
};

class TcpServer {
 public:
  TcpServer(asio::io_context& io, uint16_t port);
  void start();

 private:
  void do_accept();

  asio::io_context& io_context_;
  tcp::acceptor acceptor_;
};
