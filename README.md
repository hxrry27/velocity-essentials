# VelocityEssentials

<div align="center">

![Version](https://img.shields.io/badge/version-1.3.1-blue.svg)
![Minecraft](https://img.shields.io/badge/minecraft-1.21.7-green.svg)
![Velocity](https://img.shields.io/badge/velocity-3.3.0-purple.svg)
![License](https://img.shields.io/badge/license-MIT-orange.svg)

**A comprehensive network management plugin for Velocity proxy server networks**

*Seamlessly connect your Minecraft servers with advanced features like server join and quit memory, Discord integration, network-wide AFK synchronization, and powerful stats tracking.*

[Features](#-features) • [Installation](#-installation) • [Configuration](#-configuration) • [Commands](#-commands) • [API](#-api) • [Support](#-support)

</div>

---

## ✨ Features

### 🎯 Core Features

#### **Server Memory System**
- Automatically sends players to their last visited server on rejoin to the network
- Configurable fallback servers for new players or unavailable servers
- Blacklist certain servers from being remembered (useful for event/temp servers)
- Permission based bypass system

#### **Comprehensive Discord Integration**
- Real-time webhook notifications for:
  - Player joins/leaves with custom embed messages
  - Server switches with visual indicators
  - First-time player celebrations
  - Chat relay with permission-group-based prefixes
  - AFK status updates
- Player head avatars in Discord messages
- Fully customizable webhook appearance

#### **Network-Wide AFK System**
- Synchronized AFK status across all servers
- Auto-AFK after configurable idle time
- Custom AFK messages with `/afk [message]`
- Optional kick for extended AFK periods
- Sleep percentage exclusion (Paper/Purpur)
- Discord notifications for AFK status changes
- Comprehensive permissions system for choosing what players have access to

#### **Custom In-Game Network Messages**
- Optional supression of vanilla join/leave messages
- Network-wide server switch announcements
- First-time player welcome messages
- Fully customizable with MiniMessage formatting
- Per-server message suppression (if needed)

### 📊 Advanced Features

#### **Stats System** 
- Comprehensive Minecraft statistics tracking
- Multi-server stat aggregation
- SQLite database with efficient caching
- Real-time leaderboards for any stat
- REST API for website integration
- Automatic Mojang API username resolution

#### **Events System**
- Create time-based stats competitions
- Automatic baseline snapshots
- Real-time progress tracking
- Final rankings and results

### 🔧 Technical Features
- **Lightweight** - Minimal performance impact
- **Modular** - Enable/disable features as needed
- **Database** - Built-in SQLite, no external database required
- **Cross-Server Communication** - Plugin messaging channel for near instant updates
- **Permission Support** - Comprehensive and fine-grained permission control
- **Debug Mode** - Toggleable logging for troubleshooting

---

## 📦 Installation

### Requirements
- **Velocity** 3.3.0 or higher
- **Paper/Purpur** 1.21.7+ for backend servers
- **Java** 21 or higher

### Quick Start

1. **Download the latest release**
   - `VelocityEssentials-1.X.X.jar` → Velocity proxy
   - `VelocityEssentials-Backend-1.X.X.jar` → Each backend server

2. **Install on Velocity**
   ```
   /velocity/plugins/VelocityEssentials-1.3.1.jar
   ```

3. **Install on each backend server**
   ```
   /server/plugins/VelocityEssentials-Backend-1.3.1.jar
   ```

4. **Configure the backend plugin** on each server:
   ```yaml
   # backend/config.yml
   server-name: "smp"  # Must match Velocity server names!
   ```

5. **Restart all servers** and configure as needed

---

## ⚙️ Configuration

### Velocity Configuration (`velocity/plugins/VelocityEssentials/config.yml`)

<details>
<summary>📁 Click to expand full configuration</summary>

```yaml
# Server Memory - Remember player's last server
server-memory:
  enabled: true
  fallback-server: "lobby"
  first-join-server: "lobby"
  remember-days: 30
  blacklisted-servers:
    - "event"
    - "temp"

# Discord Webhooks
discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/..."
  username: "VelocityEssentials"
  show-afk: true
  
  # Chat Relay
  chat-relay: true
  use-player-head-for-chat: true
  chat-prefixes:
    "group.owner": "**Owner**"
    "group.admin": "**Admin**"
    "group.member": "**Member**"

# Stats System
stats:
  enabled: true
  update-interval: 30
  servers:
    survival: "/path/to/survival/world/stats"
    resource: "/path/to/resource/world/stats"
  
  # Web API
  api:
    enabled: true
    port: 8080
    auth-key: "change-this-secure-key"

# Custom Messages
messages:
  custom-enabled: true
  show-join: true
  show-leave: true
  show-switch: true
  suppress-vanilla: true
```

</details>

### Backend Configuration (`server/plugins/VelocityEssentials-Backend/config.yml`)

<details>
<summary>📁 Click to expand backend configuration</summary>

```yaml
# CRITICAL: Must match Velocity server name exactly!
server-name: "smp"  

# Vanilla message suppression
suppress-vanilla-join: true
suppress-vanilla-quit: true

# AFK System
afk:
  enabled: true
  auto-afk-time: 300  # 5 minutes
  broadcast: true
  cancel-on-move: true
  exclude-from-sleep: true  # Paper/Purpur only
  
  kick:
    enabled: false
    time: 600  # 10 minutes after AFK

# Network messaging
enable-network-messages: true

# Chat processing (requires PlaceholderAPI)
enable-chat-processing: false
```

</details>

---

## 🎮 Commands

### Velocity Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/ve reload` | `velocityessentials.admin.reload` | Reload configuration |
| `/ve info <player>` | `velocityessentials.admin.info` | View player information |
| `/ve test <server>` | `velocityessentials.admin.test` | Test backend connection |
| `/ve debug` | `velocityessentials.admin.debug` | Show debug information |

### Backend Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/afk` | `velocityessentials.afk` | Toggle AFK status |
| `/afk [message]` | `velocityessentials.afk.message` | Toggle AFK status with a message |

### Event Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/event create <name> <stat> <duration>` | `velocityessentials.events.create` | Create new event |
| `/event list` | `velocityessentials.events.view` | List all events |
| `/event leaderboard <event>` | `velocityessentials.events.view` | View event leaderboard |
| `/event start <event>` | `velocityessentials.events.manage` | Start an event |
| `/event stop <event>` | `velocityessentials.events.manage` | Stop an event |

---

## 🔑 Permissions

### Core Permissions
- `velocityessentials.admin` - Access to all admin commands
- `velocityessentials.bypass` - Bypass server memory system
- `velocityessentials.silent` - Silent join/leave/switch

### AFK Permissions
- `velocityessentials.afk` - Use /afk command
- `velocityessentials.afk.message` - Set custom AFK messages
- `velocityessentials.afk.exempt` - Exempt from auto-AFK
- `velocityessentials.afk.kickexempt` - Exempt from AFK kick

### Event Permissions
- `velocityessentials.events.view` - View events and leaderboards
- `velocityessentials.events.create` - Create new events
- `velocityessentials.events.manage` - Start/stop events
- `velocityessentials.events.admin` - Full event control

---

## 🌐 Stats API

VelocityEssentials includes a REST API for integrating stats with your website.

### Endpoints

```http
GET /api/stats/player/{username}
Authorization: Bearer {api-key}
```

```http
GET /api/stats/top/{stat-key}?limit=10
Authorization: Bearer {api-key}
```

```http
GET /api/stats/event/{event-name}
Authorization: Bearer {api-key}
```

### Example Response

```json
{
  "success": true,
  "servers": {
    "survival": {
      "minecraft:mined:minecraft:diamond_ore": 127,
      "minecraft:custom:minecraft:play_time": 486000
    }
  }
}
```

---

## 🚀 Advanced Setup

### Docker Compose Example

For Docker deployments:

```yaml
services:
  velocity-container:
    container_name: velocity-container
    image: itzg/mc-proxy:latest
    environment:
      TYPE: VELOCITY
    volumes:
      - ./velocity:/server
      - ./smp/world/stats:/stats/smp:ro
      - ./creative/world/stats:/stats/creative:ro
    ports:
      - "XYZ:XYZ"
```
---

## 📈 Planned Features

- [ ] Web dashboard for stats visualization
- [ ] Punishment system integration
- [ ] Advanced event rewards system
- [ ] Redis support for larger networks
- [ ] Bedrock (Geyser) compatibility
- [ ] More Discord interaction features

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests.

### Building from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/velocity-essentials.git
cd velocity-essentials

# Build both plugins
./build.sh  # Linux/Mac
build.bat   # Windows

# Output JARs will be in output/ directory
```

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 💖 Support

If you find VelocityEssentials useful, consider:
- ⭐ Starring the repository
- 🐛 Reporting bugs and issues
- 💡 Suggesting new features
- 🤝 Contributing code

---

## 👤 Author

**hxrry27**

Created specifically for my community server ValeSMP and the wider Minecraft playerbase as a whole.

---

<div align="center">

Made with ❤️ for Minecraft server networks

[Back to top](#velocityessentials)

</div>
