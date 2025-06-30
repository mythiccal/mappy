[![Build Status](https://ci.loohpjames.com/job/ImageFrame/badge/icon)](https://ci.loohpjames.com/job/ImageFrame/)
# ImageFrame

https://www.spigotmc.org/resources/106031/<br>
https://modrinth.com/plugin/imageframe<br>
https://hangar.papermc.io/LOOHP/ImageFrame

Put images on maps and walls!

More information (screenshots, commands, permissions) about the plugin can be found on the Spigot page linked above.

## Built against Spigot
Built against [Spigot's API](https://www.spigotmc.org/wiki/buildtools/) (required mc versions are listed on the spigot page above).
Plugins built against Spigot usually also work with [Paper](https://papermc.io/).

## Development Builds

- [Jenkins](https://ci.loohpjames.com/job/ImageFrame/)

## Maven
```html
<repository>
  <id>loohp-repo</id>
  <url>https://repo.loohpjames.com/repository</url>
</repository>
```
```html
<dependency>
  <groupId>com.loohp</groupId>
  <artifactId>ImageFrame</artifactId>
  <version>VERSION</version>
  <scope>provided</scope>
</dependency>
```
Replace `VERSION` with the version number.

## Partnerships

### Server Hosting
**Use the link or click the banner** below to **get a 25% discount off** your first month when buying any of their gaming servers!<br>
It also **supports my development**, take it as an alternative way to donate while getting your very own Minecraft server as well!

*P.S. Using the link or clicking the banner rather than the code supports me more! (Costs you no extra!)*

**https://www.bisecthosting.com/loohp**

![https://www.bisecthosting.com/loohp](https://www.bisecthosting.com/partners/custom-banners/fc7f7b10-8d1a-4478-a23a-8a357538a180.png)

## API Usage

### Adding ImageFrame to your project

First, add ImageFrame as a dependency in your Maven project:

```xml
<!-- Repository -->
<repository>
  <id>loohp-repo</id>
  <url>https://repo.loohpjames.com/repository</url>
</repository>

<!-- Dependency -->
<dependency>
  <groupId>com.loohp</groupId>
  <artifactId>ImageFrame</artifactId>
  <version>1.5.0</version>
  <scope>provided</scope>
</dependency>
```

Make sure to replace `1.5.0` with the latest version of ImageFrame.

### Using the API

To use ImageFrame's API, you need to:

1. Add ImageFrame as a dependency to your plugin.yml:
```yaml
depend: [ImageFrame]
# or if your plugin can function without ImageFrame
softdepend: [ImageFrame]
```

2. Check if ImageFrame is present before using the API:
```java
if (Bukkit.getPluginManager().getPlugin("ImageFrame") != null) {
    // ImageFrame is installed, safe to use the API
}
```

### Creating Image Maps

ImageFrame provides an API for programmatically creating image maps. Here's an example of how to use it:

```java
import com.loohp.imageframe.api.ImageFrameAPI;
import com.loohp.imageframe.objectholders.DitheringType;
import com.loohp.imageframe.objectholders.ImageMap;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class YourPlugin {
    
    /**
     * Creates an image map from a URL and gives it to a player
     * 
     * @param player The player who will receive the map
     * @param imageUrl The URL of the image
     * @param name The name to give the map
     * @param width Width in map units (1 for single map)
     * @param height Height in map units (1 for single map)
     * @param combined Whether to create a combined map item
     */
    public void createMapForPlayer(Player player, String imageUrl, String name, int width, int height, boolean combined) {
        CompletableFuture<ImageMap> future = ImageFrameAPI.createImageMap(
            imageUrl,
            width,
            height,
            name,
            player,
            combined
        );
        
        future.thenAccept(imageMap -> {
            player.sendMessage("Successfully created your image map: " + name);
        }).exceptionally(ex -> {
            player.sendMessage("Failed to create image map: " + ex.getMessage());
            return null;
        });
    }
    
    /**
     * Creates an image map from a URL for a specific player username
     * Does not require the player to be online
     * 
     * @param username Player's username
     * @param uuid Player's UUID
     * @param imageUrl The URL of the image
     * @param name The name to give the map
     * @param width Width in map units
     * @param height Height in map units
     */
    public void createMapForUsername(String username, UUID uuid, String imageUrl, String name, int width, int height) {
        Player player = getServer().getPlayer(username);
        
        CompletableFuture<ImageMap> future = ImageFrameAPI.createImageMap(
            imageUrl,
            width,
            height,
            name,
            uuid,
            player, // This can be null if player is offline
            false,
            DitheringType.NEAREST_COLOR
        );
        
        future.thenAccept(imageMap -> {
            getLogger().info("Created image map: " + name + " for player: " + username);
            // If player is offline, they'll need to retrieve the map another way
        }).exceptionally(ex -> {
            getLogger().warning("Failed to create image map for " + username + ": " + ex.getMessage());
            return null;
        });
    }
}
```

### API Method Parameters

#### Basic Method
```java
CompletableFuture<ImageMap> createImageMap(
    String imageUrl,    // URL of the image to display on the map
    int width,          // Width in map units (1 for a single map)
    int height,         // Height in map units (1 for a single map)
    String name,        // Unique name for this image map
    Player player,      // Player who will receive and own the map
    boolean combined    // Whether to create a combined map item (true) or individual maps (false)
)
```

#### Advanced Method
```java
CompletableFuture<ImageMap> createImageMap(
    String imageUrl,          // URL of the image to display on the map
    int width,                // Width in map units
    int height,               // Height in map units 
    String name,              // Unique name for this image map
    UUID creator,             // UUID of the player who will own the map
    Player player,            // Player who will receive the map (can be null for offline players)
    boolean combined,         // Whether to create a combined map item
    DitheringType ditheringType // The dithering method to use (NEAREST_COLOR, FLOYD_STEINBERG)
)
```

### Important Notes

1. The API returns a CompletableFuture because image processing happens asynchronously
2. Images are processed in a queue - only one image per player can be processed at a time
3. Players need the `imageframe.create` permission to create maps
4. Combined maps require a single item frame of size width×height
5. If width or height is greater than 1, it creates a multi-map image
6. The map is automatically added to the ImageFrame database and can be managed with commands