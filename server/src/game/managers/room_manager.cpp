#include "game/managers/room_manager.hpp"
#include "network/tcp/tcp_server.hpp"

#include <algorithm>
#include <spdlog/spdlog.h>

RoomManager& RoomManager::Instance() {
  static RoomManager instance;
  return instance;
}

lawnmower::S2C_CreateRoomResult RoomManager::CreateRoom(
    uint32_t player_id, const std::string& player_name, std::weak_ptr<TcpSession> session,
    const lawnmower::C2S_CreateRoom& request) {
  lawnmower::S2C_CreateRoomResult result;
  RoomUpdate update;
  bool need_broadcast = false;

  if (player_id == 0) {
    result.set_success(false);
    result.set_message_create("未登录，无法创建房间");
    return result;
  }

  {
    std::lock_guard<std::mutex> lock(mutex_);
    if (player_room_.count(player_id)) {
      result.set_success(false);
      result.set_message_create("请先离开当前房间");
      return result;
    }

    Room room;
    room.room_id = next_room_id_++;
    room.name = request.room_name().empty() ? ("房间" + std::to_string(room.room_id)) : request.room_name();
    room.max_players = request.max_players() == 0 ? 4 : request.max_players();
    room.is_playing = false;

    RoomPlayer host;
    host.player_id = player_id;
    host.player_name = player_name.empty() ? ("玩家" + std::to_string(player_id)) : player_name;
    host.is_ready = false;
    host.is_host = true;
    host.session = std::move(session);
    room.players.push_back(host);

    auto [iter, inserted] = rooms_.emplace(room.room_id, std::move(room));
    player_room_[player_id] = iter->first;

    result.set_success(true);
    result.set_room_id(iter->first);
    result.set_message_create("房间创建成功");

    update = BuildRoomUpdateLocked(iter->second);
    need_broadcast = true;
  }

  if (need_broadcast) {
    SendRoomUpdate(update);
  }

  spdlog::info("Player {} created room {}", player_id, result.room_id());
  return result;
}

lawnmower::S2C_JoinRoomResult RoomManager::JoinRoom(
    uint32_t player_id, const std::string& player_name, std::weak_ptr<TcpSession> session,
    const lawnmower::C2S_JoinRoom& request) {
  lawnmower::S2C_JoinRoomResult result;
  RoomUpdate update;
  bool need_broadcast = false;

  if (player_id == 0) {
    result.set_success(false);
    result.set_message_join("请先登录");
    return result;
  }

  {
    std::lock_guard<std::mutex> lock(mutex_);
    if (player_room_.count(player_id)) {
      result.set_success(false);
      result.set_message_join("已在房间中");
      return result;
    }

    auto room_it = rooms_.find(request.room_id());
    if (room_it == rooms_.end()) {
      result.set_success(false);
      result.set_message_join("房间不存在");
      return result;
    }

    Room& room = room_it->second;
    if (room.is_playing) {
      result.set_success(false);
      result.set_message_join("房间已开始游戏");
      return result;
    }

    if (room.max_players > 0 && room.players.size() >= room.max_players) {
      result.set_success(false);
      result.set_message_join("房间已满");
      return result;
    }

    RoomPlayer player;
    player.player_id = player_id;
    player.player_name = player_name.empty() ? ("玩家" + std::to_string(player_id)) : player_name;
    player.is_ready = false;
    player.is_host = false;
    player.session = std::move(session);

    room.players.push_back(std::move(player));
    player_room_[player_id] = room.room_id;

    result.set_success(true);
    result.set_message_join("加入房间成功");

    update = BuildRoomUpdateLocked(room);
    need_broadcast = true;
  }

  if (need_broadcast) {
    SendRoomUpdate(update);
  }

  spdlog::info("Player {} joined room {}", player_id, request.room_id());
  return result;
}

lawnmower::S2C_LeaveRoomResult RoomManager::LeaveRoom(uint32_t player_id) {
  lawnmower::S2C_LeaveRoomResult result;
  RoomUpdate update;
  bool need_broadcast = false;

  {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!DetachPlayerLocked(player_id, &update)) {
      result.set_success(false);
      result.set_message_leave("玩家未在任何房间");
      return result;
    }

    result.set_success(true);
    result.set_message_leave("已离开房间");
    need_broadcast = !update.targets.empty();
  }

  if (need_broadcast) {
    SendRoomUpdate(update);
  }

  spdlog::info("Player {} left room", player_id);
  return result;
}

void RoomManager::RemovePlayer(uint32_t player_id) {
  RoomUpdate update;
  bool need_broadcast = false;
  {
    std::lock_guard<std::mutex> lock(mutex_);
    need_broadcast = DetachPlayerLocked(player_id, &update) && !update.targets.empty();
  }

  if (need_broadcast) {
    SendRoomUpdate(update);
  }
}

RoomManager::RoomUpdate RoomManager::BuildRoomUpdateLocked(const Room& room) const {
  RoomUpdate update;
  update.message.set_room_id(room.room_id);
  for (const auto& player : room.players) {
    auto* info = update.message.add_players();
    info->set_player_id(player.player_id);
    info->set_player_name(player.player_name);
    info->set_is_ready(player.is_ready);
    info->set_is_host(player.is_host);
    update.targets.push_back(player.session);
  }
  return update;
}

void RoomManager::SendRoomUpdate(const RoomUpdate& update) {
  for (const auto& weak_session : update.targets) {
    if (auto session = weak_session.lock()) {
      session->SendProto(lawnmower::MessageType::MSG_S2C_ROOM_UPDATE, update.message);
    }
  }
}

bool RoomManager::DetachPlayerLocked(uint32_t player_id, RoomUpdate* update) {
  auto mapping = player_room_.find(player_id);
  if (mapping == player_room_.end()) {
    return false;
  }

  auto room_it = rooms_.find(mapping->second);
  if (room_it == rooms_.end()) {
    player_room_.erase(mapping);
    return false;
  }

  Room& room = room_it->second;
  const auto old_size = room.players.size();
  room.players.erase(std::remove_if(room.players.begin(), room.players.end(),
                                    [player_id](const RoomPlayer& p) { return p.player_id == player_id; }),
                     room.players.end());
  const bool removed = old_size != room.players.size();
  if (!removed) {
    return false;
  }

  player_room_.erase(mapping);

  if (room.players.empty()) {
    rooms_.erase(room_it);
    return true;
  }

  if (old_size != room.players.size()) {
    EnsureHost(room);
    if (update) {
      *update = BuildRoomUpdateLocked(room);
    }
  }
  return true;
}

void RoomManager::EnsureHost(Room& room) {
  const bool has_host = std::any_of(room.players.begin(), room.players.end(),
                                    [](const RoomPlayer& player) { return player.is_host; });
  if (!room.players.empty() && !has_host) {
    room.players.front().is_host = true;
  }
}
