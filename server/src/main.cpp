#include <spdlog/spdlog.h>

#include "game/managers/game_manager.hpp"
#include "message.pb.h"
#include "network/tcp/tcp_server.hpp"

int main() {
  try {
    asio::io_context io;
    GameManager::Instance().SetIoContext(&io);
    TcpServer server(io, 7777);

    spdlog::set_level(spdlog::level::debug);
    spdlog::set_pattern("%Y-%m-%d %H:%M:%S.%e [%l] %v");
    spdlog::info("服务器启动，监听端口 7777");
    server.start();

    io.run();
  } catch (std::exception& e) {
    spdlog::error("错误: {}", e.what());
  }
  return 0;
}
