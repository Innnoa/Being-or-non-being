#pragma once

#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>
#include "../../../generated/message.pb.h"

class TcpSession;

// 房间管理器：负责创建/加入/离开房间以及广播房间成员变化
class RoomManager {
 public:
  static RoomManager& Instance();

  RoomManager(const RoomManager&) = delete;
  RoomManager& operator=(const RoomManager&) = delete;

  lawnmower::S2C_CreateRoomResult CreateRoom(uint32_t player_id, const std::string& player_name,
                                             std::weak_ptr<TcpSession> session,
                                             const lawnmower::C2S_CreateRoom& request);

  lawnmower::S2C_JoinRoomResult JoinRoom(uint32_t player_id, const std::string& player_name,
                                         std::weak_ptr<TcpSession> session,
                                         const lawnmower::C2S_JoinRoom& request);

  lawnmower::S2C_LeaveRoomResult LeaveRoom(uint32_t player_id);

  // 断线清理，不返回离开结果
  void RemovePlayer(uint32_t player_id);

 private:
  RoomManager() = default;

  struct RoomPlayer {
    uint32_t player_id = 0;
    std::string player_name;
    bool is_ready = false;
    bool is_host = false;
    std::weak_ptr<TcpSession> session;
  };

  struct Room {
    uint32_t room_id = 0;
    std::string name;
    uint32_t max_players = 0;
    bool is_playing = false;
    std::vector<RoomPlayer> players;
  };

  struct RoomUpdate {
    lawnmower::S2C_RoomUpdate message;
    std::vector<std::weak_ptr<TcpSession>> targets;
  };

  RoomUpdate BuildRoomUpdateLocked(const Room& room) const;
  void SendRoomUpdate(const RoomUpdate& update);
  bool DetachPlayerLocked(uint32_t player_id, RoomUpdate* update);
  void EnsureHost(Room& room);

  mutable std::mutex mutex_;
  uint32_t next_room_id_ = 1;
  std::unordered_map<uint32_t, Room> rooms_;
  std::unordered_map<uint32_t, uint32_t> player_room_;
};
