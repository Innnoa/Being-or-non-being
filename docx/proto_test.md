protobuf

2000+2000

1.5

2000*1.5+2000

(2000+2000)*1.5

可暴击+不可暴击









```protobuf
syntax = "proto3";
package lawnmower;

// 基础通用类型
message Vector2 {
    float x = 1; // 坐标X（像素单位）
    float y = 2; // 坐标Y（像素单位）
}

message Timestamp {
    uint64 server_time = 1; // 服务器时间戳（毫秒）
    uint32 tick = 2; // 游戏帧编号（60FPS）
}

// 游戏对象状态（网络同步核心数据）
message PlayerState {
    uint32 player_id = 1; // 玩家唯一ID
    Vector2 position = 2; // 位置
    float rotation = 3; // 朝向（角度）
    int32 health = 4; // 当前血量
    int32 max_health = 5; // 最大血量
    int32 level = 6; // 等级
    int32 exp = 7; // 当前经验
    int32 exp_to_next = 8; // 升级所需经验
    repeated uint32 equipped_skills = 9; // 已装备技能ID列表
    bool is_alive = 10; // 是否存活
}

message EnemyState {
    uint32 enemy_id = 1; // 敌人实例ID
    uint32 type_id = 2; // 敌人类型ID（关联本地配置）
    Vector2 position = 3; // 位置
    int32 health = 4; // 当前血量
    int32 max_health = 5; // 最大血量
    bool is_alive = 6; // 是否存活
    uint32 wave_id = 7; // 所属波次
}

message SkillState {
    uint32 skill_id = 1; // 技能ID（关联本地配置）
    uint32 caster_id = 2; // 释放者ID（玩家/敌人）
    Vector2 cast_position = 3; // 释放位置
    float duration = 4; // 持续时间（秒）
    float range = 5; // 范围（像素）
    bool is_active = 6; // 是否生效
}

message ItemState {
    uint32 item_id = 1; // 道具实例ID
    uint32 type_id = 2; // 道具类型ID（关联本地配置）
    Vector2 position = 3; // 位置
    bool is_picked = 4; // 是否被拾取
}

// 网络消息
message C2S_PlayerInput {
    uint32 player_id = 1;
    Vector2 move_direction = 2; // 移动方向
    bool is_attacking = 3; // 是否攻击
    uint32 skill_id = 4; // 释放的技能ID（0表示未释放）
    Timestamp input_time = 5; // 输入时间戳
}

message S2C_GameStateSync {
    Timestamp sync_time = 1; // 同步时间戳
    repeated PlayerState players = 2; // 玩家状态（仅变化的）
    repeated EnemyState enemies = 3; // 敌人状态（仅变化的）
    repeated SkillState active_skills = 4; // 生效中技能
    repeated ItemState items = 5; // 道具状态
    uint32 remaining_enemies = 6; // 剩余敌人数量
    uint32 current_wave = 7; // 当前波次
}

message S2C_CombatEvent {
    enum EventType {
        ATTACK = 0;
        DEATH = 1;
        LEVEL_UP = 2;
        DROP = 3;
    }
    EventType event_type = 1;
    uint32 target_id = 2; // 目标ID
    uint32 caster_id = 3; // 触发者ID
    int32 value = 4; // 数值（伤害/经验/道具ID）
    Vector2 event_position = 5; // 事件位置
    string effect_id = 6; // 特效资源ID（关联本地特效）
}

// 配置数据索引（仅客户端本地加载，不网络传输）
message EnemyConfig {
    uint32 type_id = 1; // 与EnemyState.type_id对应
    string name = 2;
    string texture_id = 3; // 贴图资源ID（如"zombie_normal"）
    string walk_anim_id = 4; // 行走动画ID（如"zombie_walk"）
    string die_anim_id = 5; // 死亡动画ID（如"zombie_die"）
}

message SkillConfig {
    uint32 skill_id = 1; // 与SkillState.skill_id对应
    string name = 2;
    string effect_id = 3; // 特效资源ID（如"pea_shot"）
    string icon_id = 4; // 图标资源ID（如"skill_pea"）
}

// 消息封装
message Packet {
    uint32 msg_type = 1; // 消息类型（1=登录,2=输入,3=状态同步等）
    bytes payload = 2; // 消息内容
}
```

### 本地资源关联方式说明

1. **敌人资源关联**

   - 服务器通过`EnemyState.type_id`发送敌人类型（如 1 = 普通僵尸，2 = 路障僵尸）

   - 客户端本地维护

     ```
     EnemyConfig
     ```

     配置表，通过

     ```
     type_id
     ```

     匹配对应的：

     - 贴图资源：`texture_id`映射到本地图片文件（如 "zombie_normal"→"textures/zombies/normal.png"）
     - 动画资源：`walk_anim_id`映射到精灵表动画（如 "zombie_walk"→加载 "animations/zombie_walk.atlas"）

2. **技能与特效关联**

   - 服务器通过`SkillState.skill_id`和`S2C_CombatEvent.effect_id`发送技能标识

   - 客户端通过

     ```
     SkillConfig
     ```

     匹配：

     - 技能图标：`icon_id`→"ui/skills/pea_shot.png"
     - 释放特效：`effect_id`→"particles/pea_explosion.pfx"

3. **道具资源关联**

   - 服务器通过`ItemState.type_id`发送道具类型

   - 客户端通过本地道具配置表（建议与 items_config.json 同步）匹配：

     - 道具贴图：`texture_id`→"textures/items/coin.png"
     - 拾取特效：`pick_effect_id`→"particles/pick_coin.particle"

4. **实现建议**

   - 客户端启动时加载所有配置表到内存（可预编译为二进制 protobuf 格式）

   - 创建资源管理器类，提供`getTexture(String id)`、`getAnimation(String id)`等方法

   - 示例代码（Java）：

     java

     

     运行

     

     

     

     

     ```java
     // 加载配置表
     List<EnemyConfig> enemyConfigs = loadEnemyConfigs("configs/enemies.pb");
     Map<Integer, EnemyConfig> enemyConfigMap = new HashMap<>();
     for (EnemyConfig config : enemyConfigs) {
         enemyConfigMap.put(config.getTypeId(), config);
     }
     
     // 渲染敌人时获取资源
     EnemyState enemyState = ...; // 从服务器接收
     EnemyConfig config = enemyConfigMap.get(enemyState.getTypeId());
     Texture texture = assetManager.get("textures/" + config.getTextureId() + ".png", Texture.class);
     ```

     



planner_0.1.md 和其他 2 文件

如何在游戏中使用这个数据结构文件？

在植物大战僵尸割草游戏中，Protobuf 数据结构文件的使用需要结合客户端（Java）和服务端（C++）的开发流程，核心是通过**序列化 / 反序列化**实现数据传输，并通过**ID 映射本地资源**实现游戏对象的渲染与逻辑交互。以下是具体使用方式：

### **一、前期准备：生成代码与项目集成**

1. **编译 Protobuf 文件**将 `.proto` 文件编译为对应语言的代码：

   - **C++ 服务端**：使用 `protoc` 生成 `.pb.h` 和 `.pb.cc` 文件，集成到项目中（参考开发计划中的 `proto_lib` 库配置）。

   bash

   

   运行

   

   

   

   

   ```bash
   protoc --cpp_out=gen/ messages.proto  # 生成C++代码
   ```

   

   - **Java 客户端**：通过 Gradle 插件自动生成 `.java` 类（LibGDX 项目可配置 `protobuf-gradle-plugin`），存放于 `proto/` 目录（对应项目结构中的 `proto/messages.proto`）。

2. **项目依赖配置**

   - 服务端链接 `protobuf::libprotobuf` 库（参考 `target_link_libraries(proto_lib PUBLIC protobuf::libprotobuf)`）。
   - 客户端在 `build.gradle` 中添加 Protobuf 依赖（如 `com.google.protobuf:protobuf-java:3.21.0`）。

### **二、服务端（C++）使用流程**

服务端负责**维护游戏状态**、**处理逻辑**并**同步数据**给客户端，核心步骤：

#### 1. **初始化与对象创建**

cpp



运行









```cpp
#include "messages.pb.h"
using namespace lawnmower;

// 初始化玩家状态
PlayerState player_state;
player_state.set_player_id(1001);
player_state.mutable_position()->set_x(400.0f);
player_state.mutable_position()->set_y(300.0f);
player_state.set_health(100);
player_state.set_is_alive(true);

// 初始化敌人状态（关联配置表中的类型ID）
EnemyState enemy_state;
enemy_state.set_enemy_id(2001);
enemy_state.set_type_id(1);  // 1=普通僵尸（对应本地EnemyConfig）
enemy_state.mutable_position()->set_x(600.0f);
enemy_state.set_health(30);
```

#### 2. **封装与发送消息**

通过 `Packet` 结构体封装消息类型和 payload，通过网络层（TCP/UDP）发送：

cpp



运行









```cpp
// 封装游戏状态同步消息
S2C_GameStateSync sync_msg;
sync_msg.mutable_sync_time()->set_tick(1234);  // 当前帧编号
*sync_msg.add_players() = player_state;       // 添加玩家状态
*sync_msg.add_enemies() = enemy_state;        // 添加敌人状态

// 打包为Packet
Packet packet;
packet.set_msg_type(3);  // 3=状态同步消息
packet.set_payload(sync_msg.SerializeAsString());

// 发送给客户端（假设network是自定义网络管理器）
network.send_to_all(packet.SerializeAsString());
```

#### 3. **处理客户端消息**

解析客户端发送的输入消息（如移动、攻击），更新游戏状态：

cpp



运行









```cpp
// 解析客户端输入
void handle_player_input(const std::string& data) {
    C2S_PlayerInput input;
    if (!input.ParseFromString(data)) {
        spdlog::error("输入消息解析失败");
        return;
    }

    // 根据player_id找到对应玩家，更新位置
    auto& player = game_world.get_player(input.player_id());
    float move_x = input.move_direction().x();
    float move_y = input.move_direction().y();
    player.move(move_x, move_y);
}
```

### **三、客户端（Java + LibGDX）使用流程**

客户端负责**接收消息**、**解析数据**、**关联本地资源**并**渲染游戏**，核心步骤：

#### 1. **接收与解析消息**

通过网络层（Netty）接收数据，反序列化为 Protobuf 对象：

java



运行









```java
import com.lawnmower.protobuf.lawnmower.*;

// 接收Packet并解析
public void onMessageReceived(byte[] data) {
    Packet packet = Packet.parseFrom(data);
    switch (packet.getMsgType()) {
        case 3:  // 状态同步消息
            S2C_GameStateSync sync = S2C_GameStateSync.parseFrom(packet.getPayload());
            updateGameState(sync);  // 更新游戏状态
            break;
        // 处理其他消息类型（如战斗事件、升级通知）
    }
}
```

#### 2. **关联本地资源与渲染**

通过 Protobuf 中的 `ID` 字段（如 `type_id`、`texture_id`）映射本地资源（参考资源关联说明）：

java



运行









```java
// 渲染敌人（结合EnemyConfig配置表）
public void renderEnemies(S2C_GameStateSync sync) {
    for (EnemyState enemyState : sync.getEnemiesList()) {
        // 1. 通过type_id获取本地配置
        EnemyConfig config = enemyConfigMap.get(enemyState.getTypeId());
        if (config == null) continue;

        // 2. 加载贴图和动画（资源管理器封装）
        TextureRegion texture = assetManager.getTexture(config.getTextureId());
        Animation<TextureRegion> walkAnim = assetManager.getAnimation(config.getWalkAnimId());

        // 3. 渲染到屏幕
        batch.begin();
        batch.draw(
            walkAnim.getKeyFrame(stateTime, true),
            enemyState.getPosition().getX(),
            enemyState.getPosition().getY()
        );
        batch.end();
    }
}
```

#### 3. **发送玩家输入**

将本地输入（如键盘、鼠标）封装为 Protobuf 消息发送给服务端：

java



运行









```java
// 处理移动输入并发送
public void sendMovementInput(Vector2 direction) {
    C2S_PlayerInput input = C2S_PlayerInput.newBuilder()
        .setPlayerId(localPlayerId)
        .setMoveDirection(Vector2.newBuilder()
            .setX(direction.x)
            .setY(direction.y)
            .build())
        .setIsAttacking(Gdx.input.isKeyPressed(Input.Keys.SPACE))
        .build();

    // 封装为Packet发送
    Packet packet = Packet.newBuilder()
        .setMsgType(2)  // 2=输入消息
        .setPayload(input.toByteString())
        .build();
    networkManager.send(packet.toByteArray());
}
```

### **四、关键设计原则与优化**

1. **增量同步**：服务端仅发送变化的状态（如 `S2C_GameStateSync` 中只包含移动的玩家、血量变化的敌人），减少数据传输量。
2. **本地预测**：客户端收到输入后先本地移动角色，再等待服务端确认（解决网络延迟导致的卡顿）。
3. **资源预加载**：启动时加载 `EnemyConfig`、`SkillConfig` 等配置表到内存，通过 `ID` 快速索引（避免运行时 IO 操作）。
4. **消息拆分**：若单条消息过大（如波次刷新时敌人数量多），拆分到多帧发送，避免网络阻塞。

通过以上流程，Protobuf 数据结构将作为客户端与服务端的 “通信协议”，实现游戏状态的高效同步与资源的正确关联，是整个游戏联机逻辑的核心基础。
