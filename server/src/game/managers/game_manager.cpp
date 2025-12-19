#include "game/managers/game_manager.hpp"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <numbers>

#include <spdlog/spdlog.h>

namespace {
constexpr float kSpawnRadius = 120.0f;
constexpr int32_t kDefaultMaxHealth = 100;
constexpr uint32_t kDefaultAttack = 10;
constexpr uint32_t kDefaultExpToNext = 100;

float DegreesFromDirection(float x, float y) {
  if (std::abs(x) < 1e-6f && std::abs(y) < 1e-6f) {
    return 0.0f;
  }
  const float angle_rad = std::atan2(y, x);
  return angle_rad * 180.0f / std::numbers::pi_v<float>;
}

uint64_t NowMs() {
  return static_cast<uint64_t>(
      std::chrono::duration_cast<std::chrono::milliseconds>(
          std::chrono::system_clock::now().time_since_epoch())
          .count());
}
}  // namespace

GameManager& GameManager::Instance() {
  static GameManager instance;
  return instance;
}

GameManager::SceneConfig GameManager::BuildDefaultConfig() const {
  return SceneConfig{};
}

lawnmower::Timestamp GameManager::BuildTimestamp() {
  lawnmower::Timestamp ts;
  ts.set_server_time(NowMs());
  ts.set_tick(++tick_counter_);
  return ts;
}

void GameManager::PlacePlayers(const RoomManager::RoomSnapshot& snapshot,
                               Scene* scene) {
  if (scene == nullptr) {
    return;
  }

  const std::size_t count = snapshot.players.size();
  if (count == 0) {
    return;
  }

  const float center_x = static_cast<float>(scene->config.width) * 0.5f;
  const float center_y = static_cast<float>(scene->config.height) * 0.5f;

  for (std::size_t i = 0; i < count; ++i) {
    const auto& player = snapshot.players[i];
    const float angle =
        (2.0f * std::numbers::pi_v<float> * static_cast<float>(i)) /
        static_cast<float>(count);

    const float x = center_x + std::cos(angle) * kSpawnRadius;
    const float y = center_y + std::sin(angle) * kSpawnRadius;

    PlayerRuntime runtime;
    runtime.state.set_player_id(player.player_id);
    runtime.state.mutable_position()->set_x(x);
    runtime.state.mutable_position()->set_y(y);
    runtime.state.set_rotation(angle * 180.0f / std::numbers::pi_v<float>);
    runtime.state.set_health(kDefaultMaxHealth);
    runtime.state.set_max_health(kDefaultMaxHealth);
    runtime.state.set_level(1);
    runtime.state.set_exp(0);
    runtime.state.set_exp_to_next(kDefaultExpToNext);
    runtime.state.set_is_alive(true);
    runtime.state.set_attack(kDefaultAttack);
    runtime.state.set_is_friendly(true);
    runtime.state.set_role_id(0);
    runtime.state.set_critical_hit_rate(0);
    runtime.state.set_has_buff(false);
    runtime.state.set_buff_id(0);
    runtime.state.set_attack_speed(1);
    runtime.state.set_move_speed(scene->config.move_speed);

    scene->players.emplace(player.player_id, std::move(runtime));
    player_scene_[player.player_id] = snapshot.room_id;
  }
}

lawnmower::SceneInfo GameManager::CreateScene(
    const RoomManager::RoomSnapshot& snapshot) {
  std::lock_guard<std::mutex> lock(mutex_);

  // 清理旧场景（防止重复开始游戏导致映射残留）
  auto existing = scenes_.find(snapshot.room_id);
  if (existing != scenes_.end()) {
    for (const auto& [player_id, _] : existing->second.players) {
      player_scene_.erase(player_id);
    }
    scenes_.erase(existing);
  }

  Scene scene;
  scene.config = BuildDefaultConfig();
  PlacePlayers(snapshot, &scene);
  scenes_[snapshot.room_id] = std::move(scene);

  lawnmower::SceneInfo scene_info;
  scene_info.set_scene_id(snapshot.room_id);
  scene_info.set_width(scenes_[snapshot.room_id].config.width);
  scene_info.set_height(scenes_[snapshot.room_id].config.height);
  scene_info.set_tick_rate(scenes_[snapshot.room_id].config.tick_rate);
  scene_info.set_state_sync_rate(scenes_[snapshot.room_id].config.state_sync_rate);

  spdlog::info("创建场景: room_id={}, players={}", snapshot.room_id,
               snapshot.players.size());
  return scene_info;
}

bool GameManager::BuildFullState(uint32_t room_id,
                                 lawnmower::S2C_GameStateSync* sync) {
  if (sync == nullptr) {
    return false;
  }

  std::lock_guard<std::mutex> lock(mutex_);
  const auto scene_it = scenes_.find(room_id);
  if (scene_it == scenes_.end()) {
    return false;
  }

  sync->Clear();
  *sync->mutable_sync_time() = BuildTimestamp();
  sync->set_room_id(room_id);

  const Scene& scene = scene_it->second;
  for (const auto& [_, runtime] : scene.players) {
    *sync->add_players() = runtime.state;
  }
  return true;
}

bool GameManager::HandlePlayerInput(uint32_t player_id,
                                    const lawnmower::C2S_PlayerInput& input,
                                    lawnmower::S2C_GameStateSync* sync,
                                    uint32_t* room_id) {
  if (sync == nullptr || room_id == nullptr) {
    return false;
  }

  std::lock_guard<std::mutex> lock(mutex_);
  const auto mapping = player_scene_.find(player_id);
  if (mapping == player_scene_.end()) {
    return false;
  }

  const uint32_t target_room_id = mapping->second;
  auto scene_it = scenes_.find(target_room_id);
  if (scene_it == scenes_.end()) {
    player_scene_.erase(mapping);
    return false;
  }

  Scene& scene = scene_it->second;
  auto player_it = scene.players.find(player_id);
  if (player_it == scene.players.end()) {
    player_scene_.erase(mapping);
    return false;
  }

  PlayerRuntime& runtime = player_it->second;

  const uint32_t seq = input.input_seq();
  if (seq != 0 && seq <= runtime.last_input_seq) {
    return false;
  }
  if (seq != 0) {
    runtime.last_input_seq = seq;
  }

  const float dx_raw = input.move_direction().x();
  const float dy_raw = input.move_direction().y();
  const float len = std::sqrt(dx_raw * dx_raw + dy_raw * dy_raw);
  if (len < 1e-4f) {
    return false;
  }

  const float dx = dx_raw / len;
  const float dy = dy_raw / len;

  float dt_sec = 1.0f / static_cast<float>(scene.config.tick_rate);
  if (input.delta_ms() > 0) {
    dt_sec = static_cast<float>(input.delta_ms()) / 1000.0f;
  }
  dt_sec = std::clamp(dt_sec, 0.0f, 0.25f);

  const float speed = runtime.state.move_speed() > 0.0f
                          ? runtime.state.move_speed()
                          : scene.config.move_speed;

  auto* position = runtime.state.mutable_position();
  const float new_x =
      std::clamp(position->x() + dx * speed * dt_sec, 0.0f,
                 static_cast<float>(scene.config.width));
  const float new_y =
      std::clamp(position->y() + dy * speed * dt_sec, 0.0f,
                 static_cast<float>(scene.config.height));

  position->set_x(new_x);
  position->set_y(new_y);
  runtime.state.set_rotation(DegreesFromDirection(dx, dy));

  sync->Clear();
  *sync->mutable_sync_time() = BuildTimestamp();
  sync->set_room_id(target_room_id);
  *sync->add_players() = runtime.state;

  *room_id = target_room_id;
  return true;
}

void GameManager::RemovePlayer(uint32_t player_id) {
  std::lock_guard<std::mutex> lock(mutex_);
  const auto mapping = player_scene_.find(player_id);
  if (mapping == player_scene_.end()) {
    return;
  }

  const uint32_t room_id = mapping->second;
  player_scene_.erase(mapping);

  auto scene_it = scenes_.find(room_id);
  if (scene_it == scenes_.end()) {
    return;
  }

  scene_it->second.players.erase(player_id);
  if (scene_it->second.players.empty()) {
    scenes_.erase(scene_it);
  }
}
