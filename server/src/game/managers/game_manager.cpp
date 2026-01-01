#include "game/managers/game_manager.hpp"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <numbers>
#include <spdlog/spdlog.h>

#include "network/tcp/tcp_server.hpp"

namespace {
constexpr float kSpawnRadius = 120.0f;
constexpr int32_t kDefaultMaxHealth = 100;
constexpr uint32_t kDefaultAttack = 10;
constexpr uint32_t kDefaultExpToNext = 100;

// 计算朝向
float DegreesFromDirection(float x, float y) {
  if (std::abs(x) < 1e-6f && std::abs(y) < 1e-6f) {
    return 0.0f;
  }
  const float angle_rad = std::atan2(y, x);
  return angle_rad * 180.0f / std::numbers::pi_v<float>;
}

// 获取当前时间
uint64_t NowMs() {
  return static_cast<uint64_t>(
      std::chrono::duration_cast<std::chrono::milliseconds>(
          std::chrono::system_clock::now().time_since_epoch())
          .count());
}
}  // namespace

// 单例构造
GameManager& GameManager::Instance() {
  static GameManager instance;
  return instance;
}

// 构建默认配置
GameManager::SceneConfig GameManager::BuildDefaultConfig() const {
  return SceneConfig{};  // 默认构造
}

// 构建时间戳
lawnmower::Timestamp GameManager::BuildTimestamp() {
  lawnmower::Timestamp ts;
  ts.set_server_time(NowMs());
  ts.set_tick(++tick_counter_);
  return ts;
}

void GameManager::SetIoContext(asio::io_context* io) {
  io_context_ = io;
}

void GameManager::ScheduleStateSync(
    uint32_t room_id, std::chrono::milliseconds interval,
    const std::shared_ptr<asio::steady_timer>& timer) {
  if (!timer) {
    return;
  }

  timer->expires_after(interval);
  timer->async_wait(
      [this, room_id, interval, timer](const asio::error_code& ec) {
        if (ec == asio::error::operation_aborted) {
          return;
        }

        lawnmower::S2C_GameStateSync sync;
        if (!BuildFullState(room_id, &sync)) {
          StopStateSyncLoop(room_id);
          return;
        }

        const auto sessions = RoomManager::Instance().GetRoomSessions(room_id);
        for (const auto& weak_session : sessions) {
          if (auto session = weak_session.lock()) {
            session->SendProto(lawnmower::MessageType::MSG_S2C_GAME_STATE_SYNC,
                               sync);
          }
        }

        ScheduleStateSync(room_id, interval, timer);
      });
}

void GameManager::StartStateSyncLoop(uint32_t room_id) {
  if (io_context_ == nullptr) {
    spdlog::warn("未设置 io_context，无法启动状态同步定时器");
    return;
  }

  {
    std::lock_guard<std::mutex> lock(mutex_);
    auto scene_it = scenes_.find(room_id);
    if (scene_it == scenes_.end()) {
      spdlog::warn("房间 {} 未找到场景，跳过状态同步定时器", room_id);
      return;
    }
  }

  auto timer = std::make_shared<asio::steady_timer>(*io_context_);
  {
    std::lock_guard<std::mutex> lock(timer_mutex_);
    auto it = sync_timers_.find(room_id);
    if (it != sync_timers_.end()) {
      it->second->cancel();
    }
    sync_timers_[room_id] = timer;
  }

  // 测试期：固定 5 秒一次全量同步
  const auto interval = std::chrono::seconds(5);
  ScheduleStateSync(room_id, interval, timer);
  spdlog::debug("房间 {} 启动状态同步定时器，间隔 {} ms", room_id,
                std::chrono::duration_cast<std::chrono::milliseconds>(interval)
                    .count());
}

void GameManager::StopStateSyncLoop(uint32_t room_id) {
  std::shared_ptr<asio::steady_timer> timer;
  {
    std::lock_guard<std::mutex> lock(timer_mutex_);
    auto it = sync_timers_.find(room_id);
    if (it == sync_timers_.end()) {
      return;
    }
    timer = it->second;
    sync_timers_.erase(it);
  }

  if (timer) {
    timer->cancel();
  }
}

// 放置玩家？
void GameManager::PlacePlayers(const RoomManager::RoomSnapshot& snapshot,
                               Scene* scene) {
  if (scene == nullptr) {
    return;
  }

  const std::size_t count = snapshot.players.size();  // 玩家数量
  if (count == 0) {
    return;
  }

  const float center_x =
      static_cast<float>(scene->config.width) * 0.5f;  // 计算中心x
  const float center_y =
      static_cast<float>(scene->config.height) * 0.5f;  // 计算中心y

  // 遍历每一个玩家
  for (std::size_t i = 0; i < count; ++i) {
    const auto& player = snapshot.players[i];
    const float angle =
        (2.0f * std::numbers::pi_v<float> * static_cast<float>(i)) /
        static_cast<float>(count);

    // 计算实际x/y的位置
    const float x = center_x + std::cos(angle) * kSpawnRadius;
    const float y = center_y + std::sin(angle) * kSpawnRadius;

    // 设置基本信息
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

    // 将玩家对应玩家信息插入会话
    scene->players.emplace(player.player_id, std::move(runtime));
    player_scene_[player.player_id] = snapshot.room_id;  // 增加玩家对应房间
  }
}

// 创建场景
lawnmower::SceneInfo GameManager::CreateScene(
    const RoomManager::RoomSnapshot& snapshot) {
  StopStateSyncLoop(snapshot.room_id);  // 清理旧的同步定时器
  std::lock_guard<std::mutex> lock(mutex_);  // 互斥锁

  // 清理旧场景（防止重复开始游戏导致映射残留）
  auto existing = scenes_.find(snapshot.room_id);  // 房间对应会话map
  if (existing != scenes_.end()) {                 // 存在该会话
    for (const auto& [player_id, _] : existing->second.players) {
      player_scene_.erase(player_id);  // 玩家对应房间map
    }
    scenes_.erase(existing);  // 删除该会话
  }

  Scene scene;
  scene.config = BuildDefaultConfig();           // 构建默认配置
  PlacePlayers(snapshot, &scene);                // 放置玩家？
  scenes_[snapshot.room_id] = std::move(scene);  // 房间对应会话map

  lawnmower::SceneInfo scene_info;  // 场景信息
  // 设置必要信息
  scene_info.set_scene_id(snapshot.room_id);
  scene_info.set_width(scenes_[snapshot.room_id].config.width);
  scene_info.set_height(scenes_[snapshot.room_id].config.height);
  scene_info.set_tick_rate(scenes_[snapshot.room_id].config.tick_rate);
  scene_info.set_state_sync_rate(
      scenes_[snapshot.room_id].config.state_sync_rate);

  spdlog::info("创建场景: room_id={}, players={}", snapshot.room_id,
               snapshot.players.size());
  return scene_info;
}

// 构建完整的游戏状态
bool GameManager::BuildFullState(uint32_t room_id,
                                 lawnmower::S2C_GameStateSync* sync) {
  if (sync == nullptr) {
    return false;
  }

  std::lock_guard<std::mutex> lock(mutex_);     // 互斥锁
  const auto scene_it = scenes_.find(room_id);  // 房间对应会话map
  if (scene_it == scenes_.end()) {
    return false;
  }

  sync->Clear();
  *sync->mutable_sync_time() = BuildTimestamp();  // 设置新同步时间
  sync->set_room_id(room_id);                     // 设置房间id

  const Scene& scene = scene_it->second;
  for (const auto& [_, runtime] : scene.players) {
    *sync->add_players() = runtime.state;
  }
  return true;
}

// 操纵玩家输入
bool GameManager::HandlePlayerInput(uint32_t player_id,
                                    const lawnmower::C2S_PlayerInput& input,
                                    lawnmower::S2C_GameStateSync* sync,
                                    uint32_t* room_id) {
  if (sync == nullptr || room_id == nullptr) {
    return false;
  }

  std::lock_guard<std::mutex> lock(mutex_);            // 互斥锁
  const auto mapping = player_scene_.find(player_id);  // 玩家对应房间map
  if (mapping == player_scene_.end()) {
    return false;
  }

  const uint32_t target_room_id = mapping->second;  // 房间对应会话map
  auto scene_it = scenes_.find(target_room_id);
  if (scene_it == scenes_.end()) {
    player_scene_.erase(mapping);
    return false;
  }

  Scene& scene = scene_it->second;
  auto player_it = scene.players.find(player_id);  // 会话对应玩家map
  if (player_it == scene.players.end()) {
    player_scene_.erase(mapping);
    return false;
  }

  PlayerRuntime& runtime = player_it->second;

  const uint32_t seq = input.input_seq();  // 输入序号（客户端递增）
  if (seq != 0 && seq <= runtime.last_input_seq) {
    return false;
  }
  if (seq != 0) {
    runtime.last_input_seq = seq;
  }

  const float dx_raw = input.move_direction().x();  // 获取x轴向量
  const float dy_raw = input.move_direction().y();  // 获取y轴向量
  const float len =
      std::sqrt(dx_raw * dx_raw + dy_raw * dy_raw);  // 计算位移长度
  if (len < 1e-4f) {                                 // 位移小于0.00001
    return false;
  }

  const float dx = dx_raw / len;  // 计算实际x轴位移情况
  const float dy = dy_raw / len;  // 计算实际y轴唯一情况

  float dt_sec = 1.0f / static_cast<float>(scene.config.tick_rate);
  if (input.delta_ms() > 0) {
    dt_sec = static_cast<float>(input.delta_ms()) / 1000.0f;
  }
  dt_sec = std::clamp(dt_sec, 0.0f, 0.25f);

  const float speed = runtime.state.move_speed() > 0.0f
                          ? runtime.state.move_speed()
                          : scene.config.move_speed;  // 计算速度？

  auto* position = runtime.state.mutable_position();  // 位置
  const float new_x =
      std::clamp(position->x() + dx * speed * dt_sec, 0.0f,
                 static_cast<float>(scene.config.width));  // 新x
  const float new_y =
      std::clamp(position->y() + dy * speed * dt_sec, 0.0f,
                 static_cast<float>(scene.config.height));  // 新y

  position->set_x(new_x);                                    // 设置新x
  position->set_y(new_y);                                    // 设置新y
  runtime.state.set_rotation(DegreesFromDirection(dx, dy));  // 设置朝向

  sync->Clear();
  *sync->mutable_sync_time() = BuildTimestamp();  // 新的同步时间
  sync->set_room_id(target_room_id);              // 设置房间id
  *sync->add_players() = runtime.state;           // 添加玩家？

  *room_id = target_room_id;
  return true;
}

// 移除玩家
void GameManager::RemovePlayer(uint32_t player_id) {
  bool scene_removed = false;
  uint32_t room_id = 0;
  {
    std::lock_guard<std::mutex> lock(mutex_);            // 互斥锁
    const auto mapping = player_scene_.find(player_id);  // 玩家对应房间map
    if (mapping == player_scene_.end()) {                // 没找到
      return;
    }

    room_id = mapping->second;     // 获取房间id
    player_scene_.erase(mapping);  // 移除玩家对应的房间id

    auto scene_it = scenes_.find(room_id);  // 房间对应会话map
    if (scene_it == scenes_.end()) {        // 没找到
      return;
    }

    scene_it->second.players.erase(player_id);  // 移除玩家对应会话中的玩家信息
    if (scene_it->second.players.empty()) {     // 会话中玩家数量为0
      scenes_.erase(scene_it);                  // 移除该会话
      scene_removed = true;
    }
  }

  if (scene_removed) {
    StopStateSyncLoop(room_id);
  }
}
