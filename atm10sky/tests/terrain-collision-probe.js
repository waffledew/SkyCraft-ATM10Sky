// Isolated server regression check. No Skyrim or Minecraft client is required.
const ProbeCollision = Java.loadClass('dev.skycraft.world.SkyCollision')
const ProbeUUID = Java.loadClass('java.util.UUID')
const ProbeMoverType = Java.loadClass('net.minecraft.world.entity.MoverType')
const ProbeVec3 = Java.loadClass('net.minecraft.world.phys.Vec3')
const ProbeAABB = Java.loadClass('net.minecraft.world.phys.AABB')
const ProbeBlockPos = Java.loadClass('net.minecraft.core.BlockPos')
const ProbeFixture = Java.loadClass('dev.skycraft.test.CollisionFixture')
let probeMob = null
let probeTicks = 0
const probeOwner = ProbeUUID.fromString('00000000-0000-0000-0000-000000000123')
ServerEvents.loaded(event => {
  const server = event.server
  const level = server.overworld()
  server.runCommandSilent('forceload add 100000 100000 100016 100016')
  console.info('SKYCRAFT_COLLISION_PROBE verified fixture=' + ProbeFixture.seed())
  probeMob = level.createEntity('minecraft:cow')
  probeMob.mergeNbt({NoAI: 1, PersistenceRequired: 1})
  probeMob.setPosition(100004.5, 105, 100004.5)
  probeMob.spawn()
  const groundPos = new ProbeBlockPos(100004, 100, 100004)
  const ground = ProbeCollision.shapeAt(groundPos)
  let hits = 0
  const shapes = level.getBlockCollisions(probeMob, new ProbeAABB(100004, 99, 100004, 100006, 106, 100006)).iterator()
  while (shapes.hasNext()) { shapes.next(); hits++ }
  console.info('SKYCRAFT_COLLISION_PROBE ground=' + ground + ', pos=' + groundPos + ', queryHits=' + hits + ', bbox=' + probeMob.boundingBox)
  console.info('SKYCRAFT_COLLISION_PROBE started: cow at 105; streamed floor top=101; blocks=' + ProbeCollision.blockCount())
})
ServerEvents.tick(event => {
  if (probeMob === null) return
  probeMob.move(ProbeMoverType.SELF, new ProbeVec3(0, -0.1, 0))
  if (++probeTicks < 100) return
  const y = probeMob.getY()
  const ok = Math.abs(y - 101) < 0.01 && probeMob.onGround()
  console.info('SKYCRAFT_COLLISION_PROBE ' + (ok ? 'PASS' : 'FAIL') + ': y=' + y + ', grounded=' + probeMob.onGround())
  probeMob.discard()
  probeMob = null
  ProbeCollision.removeRemote(probeOwner)
  event.server.runCommandSilent('forceload remove all')
  event.server.halt(false)
})
