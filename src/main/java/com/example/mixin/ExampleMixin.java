package net.fabricmc.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExampleMod implements ClientModInitializer {
    public static final String MOD_ID = "chatstats";
    
    private static Map<String, Integer> chatDatenbank = new HashMap<>();
    private static File speicherDatei;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Erkennt Absender im Chat
    private static final Pattern CHAT_PATTERN = Pattern.compile("(?:<|\\[|\\b)([A-Za-z0-9_]{3,16})(?:>|\\]|\\b)(?:\\s*>>|\\s*:|\\s*:)");

    @Override
    public void onInitializeClient() {
        // Ordner-Pfad für die Speicherdatei ermitteln
        File configOrdner = new File(Minecraft.getInstance().gameDirectory, "config");
        speicherDatei = new File(configOrdner, "chatstats.json");
        datenLaden();

        // 1. CHAT-LISTENER: Fängt Nachrichten ab und verarbeitet den Befehl manuell
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> {
            String bereinigterText = message.getString();

            // TRICK: Wenn du selbst ".chatstats" oder "/chatstats" tippst, fangen wir es hier ab
            if (bereinigterText.startsWith(".chatstats") || bereinigterText.contains(": .chatstats")) {
                zeigeRangliste();
                return;
            }

            // Normaler Nachrichten-Zähler
            Matcher matcher = CHAT_PATTERN.matcher(bereinigterText);
            if (matcher.find()) {
                String spielerName = matcher.group(1);
                chatDatenbank.put(spielerName, chatDatenbank.getOrDefault(spielerName, 0) + 1);
                datenSpeichern();
            }
        });

        // 2. ITEM-TOOLTIP
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            String itemRegistryName = stack.getItem().toString().toLowerCase();
            if (preisDatenbankExistiertUndHat(itemRegistryName)) {
                // Hier können später Shop-Preise stehen
            }
        });
    }

    // Zeigt die Rangliste direkt in deinem privaten Chat an
    private void zeigeRangliste() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        if (chatDatenbank.isEmpty()) {
            client.player.displayClientMessage(Component.literal("§7[ChatStats] Noch keine Daten aufgezeichnet."), false);
            return;
        }

        client.player.displayClientMessage(Component.literal("§6------ §eCHAT RANGLISTE (Dauerhaft) §6------"), false);
        
        chatDatenbank.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> {
                client.player.displayClientMessage(Component.literal(
                    "§e" + entry.getKey() + "§7: §a" + entry.getValue() + " Nachrichten"
                ), false);
            });
            
        client.player.displayClientMessage(Component.literal("§6------------------------------------"), false);
    }

    private boolean preisDatenbankExistiertUndHat(String name) {
        return false; 
    }

    private static void datenSpeichern() {
        if (!speicherDatei.getParentFile().exists()) {
            speicherDatei.getParentFile().mkdirs();
        }
        try (FileWriter writer = new FileWriter(speicherDatei)) {
            gson.toJson(chatDatenbank, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void datenLaden() {
        if (!speicherDatei.exists()) return;
        try (FileReader reader = new FileReader(speicherDatei)) {
            Type type = new TypeToken<HashMap<String, Integer>>(){}.getType();
            Map<String, Integer> geladeneDaten = gson.fromJson(reader, type);
            if (geladeneDaten != null) {
                chatDatenbank = geladeneDaten;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
