package dev.skycraft.server;

import dev.skycraft.SkyCraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;

/** Writes e4mc's current public address even when the user launches startserver.bat directly. */
public final class ServerAddressWriter {
	private static final Pattern DOMAIN=Pattern.compile("Domain assigned:\\s*([a-z0-9.-]+\\.e4mc\\.link)",Pattern.CASE_INSENSITIVE);
	private static volatile boolean running;
	private ServerAddressWriter() {}

	public static synchronized void start(MinecraftServer server) {
		if(!server.isDedicatedServer() || running)return; running=true;
		Thread thread=new Thread(()->watch(server),"SkyCraft e4mc address writer"); thread.setDaemon(true); thread.start();
	}

	private static void watch(MinecraftServer server) {
		Path root=Path.of(".").toAbsolutePath().normalize(), log=root.resolve("logs/latest.log"), output=root.resolve("SERVER-ADDRESS.txt");
		long deadline=System.currentTimeMillis()+10*60*1000L;
		try {
			Files.writeString(output,"SKYCRAFT ATM10SKY SERVER ADDRESS\r\n================================\r\n\r\nWaiting for e4mc to assign an address...\r\n",StandardCharsets.US_ASCII);
			while(server.isRunning() && System.currentTimeMillis()<deadline) {
				if(Files.isRegularFile(log)) {
					String text=Files.readString(log,StandardCharsets.UTF_8); var matcher=DOMAIN.matcher(text); String address=null;
					while(matcher.find())address=matcher.group(1).toLowerCase(java.util.Locale.ROOT);
					if(address!=null) {
						String body="SKYCRAFT ATM10SKY SERVER ADDRESS\r\n================================\r\n\r\n"+address+"\r\n\r\nUpdated: "+
							LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))+"\r\n\r\nSend the address above to friends.\r\n"+
							"Friends run CHANGE-SERVER-ADDRESS.cmd and paste it.\r\nThe host continues to use localhost:25565.\r\n";
						Files.writeString(output,body,StandardCharsets.US_ASCII); SkyCraft.LOG.info("SkyCraft: wrote public server address {}",address); return;
					}
				}
				Thread.sleep(500L);
			}
		} catch(Exception ex) { SkyCraft.LOG.warn("SkyCraft: could not update SERVER-ADDRESS.txt",ex); }
		finally { running=false; }
	}
}
