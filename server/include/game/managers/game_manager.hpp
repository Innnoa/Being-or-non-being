#pragma once

#include <asio/steady_timer.hpp>
#include <chrono>
#include <cstdint>
#include <deque>
#include <memory>
#include <mutex>
#include <optional>
#include <unordered_map>
#include <vector>

#include "game/managers/room_manager.hpp"
#include "message.pb.h"

// 游戏管理器：负责场景初始化、玩家状态更新与同步
class GameManager {
 public:
  static GameManager& Instance();

  GameManager(const GameManager&) = delete;
  GameManager& operator=(const GameManager&) = delete;

  // 为指定房间创建场景并生成初始 SceneInfo（覆盖已存在的同房间场景）
  lawnmower::SceneInfo CreateScene(const RoomManager::RoomSnapshot& snapshot);

  // 构造完整的状态同步（通常用于游戏开始时的全量同步）
  bool BuildFullState(uint32_t room_id, lawnmower::S2C_GameStateSync* sync);

  // 注册 io_context（用于定时广播状态同步）
  void SetIoContext(asio::io_context* io);

  // 在游戏开始后为房间启动固定逻辑帧循环与状态同步
  void StartGameLoop(uint32_t room_id);

  // 处理玩家输入，将其入队等待逻辑帧处理；返回 false 表示未找到玩家或场景
  bool HandlePlayerInput(uint32_t player_id,
                         const lawnmower::C2S_PlayerInput& input,
                         uint32_t* room_id);

  // 玩家断线/离开时清理场景信息
  void RemovePlayer(uint32_t player_id);

 private:
  GameManager() = default;

  struct SceneConfig {  // 默认场景配置
    uint32_t width = 2000;
    uint32_t height = 2000;
    uint32_t tick_rate = 60;
    uint32_t state_sync_rate = 20;
    float move_speed = 280.0f;
  };

  struct PlayerRuntime {
    lawnmower::PlayerState state;
    uint32_t last_input_seq = 0;
    std::deque<lawnmower::C2S_PlayerInput> pending_inputs;
    bool dirty = false;
  };

  struct Scene {
    SceneConfig config;
    std::unordered_map<uint32_t, PlayerRuntime>
        players;  // 玩家对应玩家运行状态
    uint64_t tick = 0;
    uint32_t ticks_since_sync = 0;
    std::shared_ptr<asio::steady_timer> loop_timer;
  };

  SceneConfig BuildDefaultConfig() const;
  void PlacePlayers(const RoomManager::RoomSnapshot& snapshot, Scene* scene);
  void ProcessSceneTick(uint32_t room_id, float dt, uint32_t ticks_per_sync);
  void ScheduleGameTick(uint32_t room_id, std::chrono::microseconds interval,
                        const std::shared_ptr<asio::steady_timer>& timer,
                        float dt, uint32_t ticks_per_sync);
  void StopGameLoop(uint32_t room_id);

  mutable std::mutex mutex_;
  std::unordered_map<uint32_t, Scene> scenes_;           // room_id -> scene
  std::unordered_map<uint32_t, uint32_t> player_scene_;  // player_id -> room_id

  asio::io_context* io_context_ = nullptr;
};
