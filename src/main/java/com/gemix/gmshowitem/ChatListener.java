package com.gemix.gmshowitem;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ChatListener implements Listener {

    private final Cache<UUID, Long> cooldowns = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();
            
    private final long COOLDOWN_TIME_MS = 5000;
    
    private static final Pattern MATCH_PATTERN = Pattern.compile("(?i)\\[i\\]|\\[show\\]|\\[item\\]");

    // Sử dụng MONITOR để chạy sau tất cả các plugin chat, đảm bảo bọc đúng ChatRenderer cuối cùng
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("gmshowitem.use")) return;

        // Quét nhanh xem có từ khóa không, nếu không có thì bỏ qua luôn cho nhẹ
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!MATCH_PATTERN.matcher(plainMessage).find()) return;

        long now = System.currentTimeMillis();
        Long lastTime = cooldowns.getIfPresent(player.getUniqueId());
        
        if (lastTime != null && (now - lastTime < COOLDOWN_TIME_MS)) {
            long remaining = (long) Math.ceil((COOLDOWN_TIME_MS - (now - lastTime)) / 1000.0);
            player.sendActionBar(Component.text("Vui lòng chờ " + remaining + "s để hiển thị vật phẩm tiếp!", NamedTextColor.RED));
            return; // Đang hồi chiêu thì không làm gì cả
        }
        cooldowns.put(player.getUniqueId(), now);

        ItemStack item = player.getInventory().getItemInMainHand();
        Component itemComponent;
        
        if (item == null || item.isEmpty()) {
            itemComponent = Component.text("[Tay Không]", NamedTextColor.GRAY);
        } else {
            ItemStack displayItem = item.clone();
            if (displayItem.hasItemMeta()) {
                org.bukkit.inventory.meta.ItemMeta meta = displayItem.getItemMeta();
                // Ẩn tất cả flags cơ bản
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
                // Đặc biệt ở 1.21: Phải xóa sạch thuộc tính (attributes) để dứt điểm chữ "When equipped:"
                meta.setAttributeModifiers(com.google.common.collect.ArrayListMultimap.create());
                displayItem.setItemMeta(meta);
            }

            Component itemName = displayItem.displayName();
            Component amountText = displayItem.getAmount() > 1 ? Component.text(" x" + displayItem.getAmount(), NamedTextColor.GRAY) : Component.empty();
            itemComponent = Component.empty()
                    .append(Component.text("[", NamedTextColor.GRAY))
                    .append(itemName)
                    .append(amountText)
                    .append(Component.text("]", NamedTextColor.GRAY))
                    .hoverEvent(displayItem.asHoverEvent());
        }

        TextReplacementConfig config = TextReplacementConfig.builder()
                .match(MATCH_PATTERN)
                .replacement(itemComponent)
                .times(3)
                .build();

        // Bọc (Wrap) lại ChatRenderer cuối cùng (của LPC hoặc plugin khác)
        // Điều này đảm bảo HoverEvent không bị plugin chat format xóa mất
        ChatRenderer originalRenderer = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component rendered = originalRenderer.render(source, sourceDisplayName, message, viewer);
            return rendered.replaceText(config);
        });
    }
}
