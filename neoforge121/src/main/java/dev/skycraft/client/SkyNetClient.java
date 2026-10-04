package dev.skycraft.client;

import dev.skycraft.link.SkyLink;
import dev.skycraft.net.SkyNet;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only publishers, isolated so a dedicated server never resolves client classes. */
public final class SkyNetClient {
	private static final List<SkyLink.Actor> ACTORS=new ArrayList<>();
	private static final SkyLink.SkyState SKY=new SkyLink.SkyState();
	private static int nextActors,nextTerrain,nextTime;
	private static long lastTick=Long.MIN_VALUE;
	private SkyNetClient() {}

	public static void clientTick(Minecraft minecraft) {
		if(minecraft.getConnection()==null || minecraft.level==null){nextActors=nextTerrain=nextTime=0;lastTick=Long.MIN_VALUE;return;}
		long tick=minecraft.level.getGameTime(); if(tick<lastTick)nextActors=nextTerrain=nextTime=0; lastTick=tick;
		if (SkyClient.spawnContextReady() && minecraft.player != null && tick % 10 == 0 && SkyLink.readSkyState(SKY)) {
			PacketDistributor.sendToServer(new SkyNet.AreaSync(SKY.worldId));
		}
		if(tick>=nextTime && SkyLink.readSkyState(SKY) && SKY.inGame()) {
			nextTime=(int)tick+20; float hour=SKY.gameHour; var integrated=minecraft.getSingleplayerServer();
			if(integrated!=null)integrated.execute(()->SkyNet.applyTime(integrated,hour)); else PacketDistributor.sendToServer(new SkyNet.TimeSync(hour));
		}
		if(minecraft.getSingleplayerServer()!=null)return;
		if(tick>=nextActors){nextActors=(int)tick+4;if(SkyLink.readActors(ACTORS))PacketDistributor.sendToServer(new SkyNet.ActorSync(encodeActors()));}
		if(tick>=nextTerrain && minecraft.player!=null && dev.skycraft.world.SkyCollision.active()){
			nextTerrain=(int)tick+40;BlockPos center=minecraft.player.blockPosition();
			PacketDistributor.sendToServer(new SkyNet.TerrainSync(center,dev.skycraft.world.SkyCollision.snapshotAround(center,24,24,4096)));
		}
	}

	private static byte[] encodeActors(){
		int count=Math.min(ACTORS.size(),128);ByteBuffer out=ByteBuffer.allocate(count*64).order(ByteOrder.LITTLE_ENDIAN);
		for(int i=0;i<count;i++){var a=ACTORS.get(i);out.putInt(a.formId()).putInt(a.flags()).putFloat(a.x()).putFloat(a.y()).putFloat(a.z()).putFloat(a.yaw())
			.putFloat(a.width()).putFloat(a.height()).putFloat(a.healthFrac()).putInt(a.level());byte[] name=a.name().getBytes(StandardCharsets.UTF_8);
			out.put(name,0,Math.min(name.length,23));out.position((i+1)*64);}
		return out.array();
	}
}
