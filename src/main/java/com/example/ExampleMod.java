package net.fabricmc.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
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
    
    // Lokaler Speicher für die Rangliste (Spielername -> Anzahl Nachrichten)
    private static Map<String, Integer> chatDatenbank = new HashMap<>();
    private static File speicherDatei;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Regex-Muster, um den Spielernamen aus typischen Server-Chats zu filtern
    // Erkennt Formate wie: <Spielername> Text, [Rang] Spielername: Text, Spielername >> Text
    private static final Pattern CHAT_PATTERN = Pattern.compile("(?:<|\\[|\\b)([A-Za-z0-9_]{3,16})(?:>|\\]|\\b)(?:\\s*>>|\\s*:|\\s*:)");

    @Override
    public void onInitializeClient() {
        // 1. Speicherpfad im Minecraft-Config-Ordner festlegen
        Path configPfad = FabricLoader.getInstance().getConfigDir();
        speicherDatei = configPfad.resolve("chatstats.json").toFile();
        
        // Gespeicherte Daten beim Starten laden
        datenLaden();

        // 2. CHAT-LISTENER: Eingehende Nachrichten analysieren
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> {
            String bereinigterText = message.getString();
            Matcher matcher = CHAT_PATTERN.matcher(bereinigterText);
            
            if (matcher.find()) {
                String spielerName = matcher.group(1);
                
                // Nachricht hochzählen und in der Datenbank speichern
                chatDatenbank.put(spielerName, chatDatenbank.getOrDefault(spielerName, 0) + 1);
                
                // Sofort auf der Festplatte sichern
                datenSpeichern();
            }
        });

        // 3. IN-GAME BEFEHL: /chatstats registrieren
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("chatstats")
                .executes(context -> {
                    if (chatDatenbank.isEmpty()) {
                        context.getSource().sendFeedback(Component.literal("§7[ChatStats] Noch keine Daten aufgezeichnet."));
                        return 1;
                    }

                    context.getSource().sendFeedback(Component.literal("§6------ §eCHAT RANGLISTE (Dauerhaft) §6------"));
                    
                    // Sortieren nach den meisten Nachrichten und die Top 10 ausgeben
                    chatDatenbank.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(10)
                        .forEach(entry -> {
                            context.getSource().sendFeedback(Component.literal(
                                "§e" + entry.getKey() + "§7: §a" + entry.getValue() + " Nachrichten"
                            ));
                        });
                        
                    context.getSource().sendFeedback(Component.literal("§6------------------------------------"));
                    return 1;
                })
            );
        });
    }

    // Speichert die Map als JSON-Datei ab
    private static void datenSpeichern() {
        try (FileWriter writer = new FileWriter(speicherDatei)) {
            gson.toJson(chatDatenbank, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Lädt die Map aus der JSON-Datei
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
