package dev.skycraft.client;

import dev.skycraft.combat.SkyCombat;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.client.mixin.KeyboardHandlerAccessor;
import dev.skycraft.client.mixin.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;

/**
 * Replays Skyrim-captured input into Minecraft's own input handlers, as if the (hidden) MC
 * window had focus. Keeps a virtual keyboard so InputConstants.isKeyDown() still works.
 */
public final class InputBridge {
	private static final boolean[] KEYS = new boolean[512];
	private static final boolean[] BUTTONS = new boolean[8];
	private static double cursorX, cursorY;
	private static int modifiers;
	private static int clickLogs;

	private InputBridge() {
	}

	public static boolean isKeyDown(int scancode) {
		return scancode >= 0 && scancode < KEYS.length && KEYS[scancode];
	}

	public static void drain(Minecraft minecraft) {
		SkyLink.drainInput((type, code, a, b, c) -> dispatch(minecraft, type, code, a, b, c));
	}

	private static void dispatch(Minecraft minecraft, int type, int code, int a, int b, int c) {
		long handle = minecraft.getWindow().getWindow();
		switch (type) {
			case Proto.IN_KEY -> key(minecraft, handle, code, a != 0);
			case Proto.IN_MOUSE_BUTTON -> {
				if (code > 0 && code < BUTTONS.length) {
					BUTTONS[code] = a != 0;
				}
				if (a != 0 && clickLogs++ < 20) {
					var hit = minecraft.hitResult;
					dev.skycraft.SkyCraft.LOG.info("SkyCraft: click {} -> {} {} (grabbed {}, screen {})", code, hit == null ? "null" : hit.getType(),
						hit instanceof net.minecraft.world.phys.EntityHitResult eh ? eh.getEntity().getName().getString() : hit == null ? "" : hit.getLocation(),
						minecraft.mouseHandler.isMouseGrabbed(), minecraft.screen);
				}
				((MouseHandlerAccessor) minecraft.mouseHandler).skycraft$onPress(handle, mouseButton(code), a != 0 ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, modifiers);
			}
			case Proto.IN_SCROLL -> ((MouseHandlerAccessor) minecraft.mouseHandler).skycraft$onScroll(handle, 0.0, a / 120.0);
			case Proto.IN_CURSOR -> {
				double dx = a - cursorX;
				double dy = b - cursorY;
				cursorX = a;
				cursorY = b;
				((MouseHandlerAccessor) minecraft.mouseHandler).skycraft$onMove(handle, a, b);
			}
			case Proto.IN_TEXT -> {
				if (minecraft.screen != null) {
					((KeyboardHandlerAccessor) minecraft.keyboardHandler).skycraft$charTyped(handle, a, modifiers);
				}
			}
			case Proto.IN_RELEASE_ALL -> releaseAll();
			case Proto.IN_HURT -> hurt(minecraft, code, a / 100.0F, b, c);
			case Proto.IN_OPEN_MENU -> {
				if (minecraft.screen == null && minecraft.player != null) {
					releaseAll();
					minecraft.setScreen(new PauseScreen(true));
				}
			}
			default -> {
			}
		}
	}

	/** Skyrim hit the player: apply it as Minecraft damage on the integrated server (or the host's). */
	private static void hurt(Minecraft minecraft, int kind, float skyrimDamage, int attacker, int flags) {
		var server = minecraft.getSingleplayerServer();
		if (minecraft.player == null) {
			return;
		}
		if (server == null) {
			// A guest in a friend's world: the host's server applies it.
			net.neoforged.neoforge.network.PacketDistributor.sendToServer(
				new dev.skycraft.net.SkyNet.Hurt(kind, skyrimDamage, attacker, flags));
			return;
		}
		var uuid = minecraft.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				SkyCombat.hurtPlayer(player, kind, skyrimDamage, attacker, flags);
			}
		});
	}

	private static void key(Minecraft minecraft, long handle, int scancode, boolean down) {
		if (scancode <= 0 || scancode >= KEYS.length) {
			return;
		}
		int keycode = glfwKey(scancode);
		if (keycode == GLFW.GLFW_KEY_UNKNOWN || keycode >= KEYS.length) {
			return;
		}
		boolean wasDown = KEYS[keycode];
		KEYS[keycode] = down;
		updateModifiers();
		int action = down ? (wasDown ? GLFW.GLFW_REPEAT : GLFW.GLFW_PRESS) : GLFW.GLFW_RELEASE;
		minecraft.keyboardHandler.keyPress(handle, keycode, scancode, action, modifiers);
	}

	private static void updateModifiers() {
		int m = 0;
		if (KEYS[GLFW.GLFW_KEY_LEFT_SHIFT] || KEYS[GLFW.GLFW_KEY_RIGHT_SHIFT]) m |= GLFW.GLFW_MOD_SHIFT;
		if (KEYS[GLFW.GLFW_KEY_LEFT_CONTROL] || KEYS[GLFW.GLFW_KEY_RIGHT_CONTROL]) m |= GLFW.GLFW_MOD_CONTROL;
		if (KEYS[GLFW.GLFW_KEY_LEFT_ALT] || KEYS[GLFW.GLFW_KEY_RIGHT_ALT]) m |= GLFW.GLFW_MOD_ALT;
		if (KEYS[GLFW.GLFW_KEY_LEFT_SUPER] || KEYS[GLFW.GLFW_KEY_RIGHT_SUPER]) m |= GLFW.GLFW_MOD_SUPER;
		modifiers = m;
	}

	/** Lift every key and button we think is held (focus moved to Skyrim, link dropped, ...). */
	public static void releaseAll() {
		Minecraft minecraft = Minecraft.getInstance();
		long handle = minecraft.getWindow().getWindow();
		for (int key = 0; key < KEYS.length; key++) {
			if (KEYS[key]) {
				KEYS[key] = false;
				updateModifiers();
				minecraft.keyboardHandler.keyPress(handle, key, 0, GLFW.GLFW_RELEASE, modifiers);
			}
		}
		for (int button = 1; button < BUTTONS.length; button++) {
			if (BUTTONS[button]) {
				BUTTONS[button] = false;
				((MouseHandlerAccessor) minecraft.mouseHandler).skycraft$onPress(handle, mouseButton(button), GLFW.GLFW_RELEASE, 0);
			}
		}
	}

	private static int mouseButton(int sdlButton) {
		return switch (sdlButton) {
			case 1 -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
			case 2 -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
			case 3 -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
			default -> sdlButton - 1;
		};
	}

	/** SDL scancodes are USB-HID positions; translate the keys Skyrim sends to GLFW keycodes. */
	private static int glfwKey(int scancode) {
		if (scancode >= 4 && scancode <= 29) return GLFW.GLFW_KEY_A + scancode - 4;
		if (scancode >= 30 && scancode <= 38) return GLFW.GLFW_KEY_1 + scancode - 30;
		if (scancode == 39) return GLFW.GLFW_KEY_0;
		if (scancode >= 58 && scancode <= 69) return GLFW.GLFW_KEY_F1 + scancode - 58;
		return switch (scancode) {
			case 40 -> GLFW.GLFW_KEY_ENTER; case 41 -> GLFW.GLFW_KEY_ESCAPE; case 42 -> GLFW.GLFW_KEY_BACKSPACE;
			case 43 -> GLFW.GLFW_KEY_TAB; case 44 -> GLFW.GLFW_KEY_SPACE; case 45 -> GLFW.GLFW_KEY_MINUS;
			case 46 -> GLFW.GLFW_KEY_EQUAL; case 47 -> GLFW.GLFW_KEY_LEFT_BRACKET; case 48 -> GLFW.GLFW_KEY_RIGHT_BRACKET;
			case 49 -> GLFW.GLFW_KEY_BACKSLASH; case 51 -> GLFW.GLFW_KEY_SEMICOLON; case 52 -> GLFW.GLFW_KEY_APOSTROPHE;
			case 53 -> GLFW.GLFW_KEY_GRAVE_ACCENT; case 54 -> GLFW.GLFW_KEY_COMMA; case 55 -> GLFW.GLFW_KEY_PERIOD;
			case 56 -> GLFW.GLFW_KEY_SLASH; case 57 -> GLFW.GLFW_KEY_CAPS_LOCK; case 70 -> GLFW.GLFW_KEY_PRINT_SCREEN;
			case 71 -> GLFW.GLFW_KEY_SCROLL_LOCK; case 72 -> GLFW.GLFW_KEY_PAUSE; case 73 -> GLFW.GLFW_KEY_INSERT;
			case 74 -> GLFW.GLFW_KEY_HOME; case 75 -> GLFW.GLFW_KEY_PAGE_UP; case 76 -> GLFW.GLFW_KEY_DELETE;
			case 77 -> GLFW.GLFW_KEY_END; case 78 -> GLFW.GLFW_KEY_PAGE_DOWN; case 79 -> GLFW.GLFW_KEY_RIGHT;
			case 80 -> GLFW.GLFW_KEY_LEFT; case 81 -> GLFW.GLFW_KEY_DOWN; case 82 -> GLFW.GLFW_KEY_UP;
			case 83 -> GLFW.GLFW_KEY_NUM_LOCK; case 84 -> GLFW.GLFW_KEY_KP_DIVIDE; case 85 -> GLFW.GLFW_KEY_KP_MULTIPLY;
			case 86 -> GLFW.GLFW_KEY_KP_SUBTRACT; case 87 -> GLFW.GLFW_KEY_KP_ADD; case 88 -> GLFW.GLFW_KEY_KP_ENTER;
			case 89 -> GLFW.GLFW_KEY_KP_1; case 90 -> GLFW.GLFW_KEY_KP_2; case 91 -> GLFW.GLFW_KEY_KP_3;
			case 92 -> GLFW.GLFW_KEY_KP_4; case 93 -> GLFW.GLFW_KEY_KP_5; case 94 -> GLFW.GLFW_KEY_KP_6;
			case 95 -> GLFW.GLFW_KEY_KP_7; case 96 -> GLFW.GLFW_KEY_KP_8; case 97 -> GLFW.GLFW_KEY_KP_9;
			case 98 -> GLFW.GLFW_KEY_KP_0; case 99 -> GLFW.GLFW_KEY_KP_DECIMAL;
			case 224 -> GLFW.GLFW_KEY_LEFT_CONTROL; case 225 -> GLFW.GLFW_KEY_LEFT_SHIFT; case 226 -> GLFW.GLFW_KEY_LEFT_ALT;
			case 227 -> GLFW.GLFW_KEY_LEFT_SUPER; case 228 -> GLFW.GLFW_KEY_RIGHT_CONTROL; case 229 -> GLFW.GLFW_KEY_RIGHT_SHIFT;
			case 230 -> GLFW.GLFW_KEY_RIGHT_ALT; case 231 -> GLFW.GLFW_KEY_RIGHT_SUPER;
			default -> GLFW.GLFW_KEY_UNKNOWN;
		};
	}
}
