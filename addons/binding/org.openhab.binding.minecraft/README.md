# Openhab-Minecraft-Binding

Binding integrating Minecraft with openhab through the spigot plugin 
[here](https://github.com/ibaton/bukkit-openhab-plugin/releases/download/1.5/OHMinecraft.jar),
[source](https://github.com/ibaton/bukkit-openhab-plugin/tree/master).
The binding allows reading of server and player data. The binding also keeps track of redstone power going bellow signs and links them to Switch item.

## Youtube Video
https://www.youtube.com/watch?v=TdvkTorzkXU&feature=youtu.be

## Discovery

The Minecraft binding will automatically find all Minecraft servers running [this plugin](https://github.com/ibaton/bukkit-openhab-plugin/releases/download/1.5/OHMinecraft.jar) on the local network. Server can be added manually if it isn't found automatically. 

## Channels

Depending on the thing it supports different Channels

### Server
| Channel Type ID | Item Type    | Description  |
|------------------|------------------------|--------------|
| name | String | Name of Minecraft server |
| online | Switch | Servers online status |
| bukkitVersion | String | The bukkit version running on server |
| version | String | The Minecraft version running on server |
| players | Number | The number of players on server |
| maxPlayers | Number | The maximum number of players on server |

### Player
| Channel Type ID | Item Type    | Description  |
|------------------|------------------------|--------------|
| playerName | String | Name of minecraft player |
| playerOnline | Switch | Is player connected to server |
| playerLevel | Number | The level of player |
| playerTotalExperience | Number | The total experience of player |
| playerExperiencePercentage | Number | Percentage of experience bar filled for next level |
| playerHealth | Number | The health of player |
| playerWalkSpeed | Number | The speed of player |
| playerLocationX | Number | The x location of player |
| playerLocationY | Number | The y location of player |
| playerLocationZ | Number | The z location of player |

### Sign
| Channel Type ID | Item Type    | Description  |
|------------------|------------------------|--------------|
| signActive | Switch | Does sign have powered redstone bellow it |
