/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2025. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2025. Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.loohp.imageframe.api;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.DitheringType;
import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.objectholders.ImageMapCreationTask;
import com.loohp.imageframe.objectholders.ImageMapCreationTaskManager;
import com.loohp.imageframe.objectholders.Scheduler;
import com.loohp.imageframe.objectholders.URLAnimatedImageMap;
import com.loohp.imageframe.objectholders.URLStaticImageMap;
import com.loohp.imageframe.upload.ImageUploadManager;
import com.loohp.imageframe.upload.PendingUpload;
import com.loohp.imageframe.utils.HTTPRequestUtils;
import com.loohp.imageframe.utils.MapUtils;
import com.loohp.imageframe.utils.PlayerUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * API for the ImageFrame plugin
 */
public class ImageFrameAPI {

    /**
     * Creates an image map using a URL and places it in a player's inventory
     *
     * @param imageUrl    URL of the image to use
     * @param width       Width of the image map in map units
     * @param height      Height of the image map in map units
     * @param name        Name of the image map
     * @param player      Player who will receive the map
     * @param combined    Whether to create a combined map item
     * @return A Future that resolves to the created ImageMap, or null if creation failed
     */
    public static CompletableFuture<ImageMap> createImageMap(String imageUrl, int width, int height, String name, Player player, boolean combined) {
        return createImageMap(imageUrl, width, height, name, player.getUniqueId(), player, combined, DitheringType.NEAREST_COLOR);
    }

    /**
     * Creates an image map using a URL
     *
     * @param imageUrl       URL of the image to use
     * @param width          Width of the image map in map units
     * @param height         Height of the image map in map units
     * @param name           Name of the image map
     * @param creator        UUID of the creator
     * @param player         Player who will receive the map (can be null)
     * @param combined       Whether to create a combined map item
     * @param ditheringType  Dithering type to use for the image
     * @return A Future that resolves to the created ImageMap, or null if creation failed
     */
    public static CompletableFuture<ImageMap> createImageMap(String imageUrl, int width, int height, String name, UUID creator, Player player, boolean combined, DitheringType ditheringType) {
        CompletableFuture<ImageMap> future = new CompletableFuture<>();
        
        // Input validation
        if (width * height > ImageFrame.mapMaxSize) {
            future.completeExceptionally(new IllegalArgumentException("Map size exceeds maximum allowed size"));
            return future;
        }
        
        if (!ImageFrame.isURLAllowed(imageUrl)) {
            future.completeExceptionally(new IllegalArgumentException("URL is not allowed"));
            return future;
        }
        
        // Check player limits if applicable
        if (player != null) {
            int limit = ImageFrame.getPlayerCreationLimit(player);
            if (limit >= 0 && ImageFrame.imageMapManager.getFromCreator(creator).size() >= limit) {
                future.completeExceptionally(new IllegalArgumentException("Player has reached creation limit"));
                return future;
            }
            
            // Check for duplicate name
            if (ImageFrame.imageMapManager.getFromCreator(creator).stream().anyMatch(each -> each.getName().equalsIgnoreCase(name))) {
                future.completeExceptionally(new IllegalArgumentException("An image map with this name already exists"));
                return future;
            }
        }
        
        // Take maps from inventory if required
        int takenMaps = 0;
        if (player != null && ImageFrame.requireEmptyMaps) {
            takenMaps = MapUtils.removeEmptyMaps(player, width * height, true);
            if (takenMaps < 0) {
                future.completeExceptionally(new IllegalArgumentException("Not enough empty maps in inventory"));
                return future;
            }
        }
        
        // Process the request asynchronously
        final int finalTakenMaps = takenMaps;
        Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
            ImageMapCreationTask<ImageMap> creationTask = null;
            String url = imageUrl;
            
            try {
                // Handle upload URL case
                if (ImageFrame.uploadServiceEnabled && url.equalsIgnoreCase("upload")) {
                    UUID user = creator;
                    try {
                        PendingUpload pendingUpload = ImageFrame.imageUploadManager.newPendingUpload(user);
                        url = pendingUpload.getFileBlocking().toURI().toURL().toString();
                    } catch (Exception e) {
                        if (finalTakenMaps > 0 && player != null) {
                            PlayerUtils.giveItem(player, new ItemStack(Material.MAP, finalTakenMaps));
                        }
                        future.completeExceptionally(new IOException("Upload failed"));
                        return;
                    }
                }
                
                // Check file size
                if (HTTPRequestUtils.getContentSize(url) > ImageFrame.maxImageFileSize) {
                    if (finalTakenMaps > 0 && player != null) {
                        PlayerUtils.giveItem(player, new ItemStack(Material.MAP, finalTakenMaps));
                    }
                    future.completeExceptionally(new IOException("Image exceeds maximum file size"));
                    return;
                }
                
                // Determine image type and create appropriate map
                String imageType = HTTPRequestUtils.getContentType(url);
                if (imageType == null) {
                    imageType = "";
                } else {
                    imageType = imageType.trim();
                }
                
                String finalUrl = url;
                String finalImageType = imageType;
                
                // Create and queue the map task
                creationTask = ImageFrame.imageMapCreationTaskManager.enqueue(creator, name, width, height, () -> {
                    if (finalImageType.equals(MapUtils.GIF_CONTENT_TYPE)) {
                        return URLAnimatedImageMap.create(ImageFrame.imageMapManager, name, finalUrl, width, height, ditheringType, creator).get();
                    } else {
                        return URLStaticImageMap.create(ImageFrame.imageMapManager, name, finalUrl, width, height, ditheringType, creator).get();
                    }
                });
                
                // Get the result and add it to the manager
                ImageMap imageMap = creationTask.get();
                ImageFrame.imageMapManager.addMap(imageMap);
                
                // Handle giving the map to player if requested
                if (player != null) {
                    if (combined) {
                        ImageFrame.combinedMapItemHandler.giveCombinedMap(imageMap, player);
                    } else {
                        imageMap.giveMaps(player, ImageFrame.mapItemFormat);
                    }
                }
                
                creationTask.complete("Success");
                future.complete(imageMap);
                
            } catch (ImageMapCreationTaskManager.EnqueueRejectedException e) {
                if (finalTakenMaps > 0 && player != null) {
                    PlayerUtils.giveItem(player, new ItemStack(Material.MAP, finalTakenMaps));
                }
                future.completeExceptionally(new IOException("Another image map is already being processed"));
            } catch (Exception e) {
                if (finalTakenMaps > 0 && player != null) {
                    PlayerUtils.giveItem(player, new ItemStack(Material.MAP, finalTakenMaps));
                }
                if (creationTask != null) {
                    creationTask.complete("Failed");
                }
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
}