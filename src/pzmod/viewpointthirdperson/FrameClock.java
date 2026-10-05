package pzmod.viewpointthirdperson;

import zombie.GameTime;

// The game moves bodies and vehicles on by the previous frame's length, so at uneven frame rates
// they go the wrong distance for the frame shown and the world judders past a camera hung from
// them. What the camera follows is drawn on a clock that runs with the frames and is held to the
// game's. Main thread.
public class FrameClock {
    static final float HOLD = 0.5f;
    static final float SPAN = 0.03f;
    static double game, drawn;
    // How far the drawn clock runs ahead of the game's, in seconds.
    static float ahead;
    static long ticked = Long.MIN_VALUE;

    // Once a frame; dt is the time since the last frame.
    static void tick(long frame, float dt) {
        if (frame == ticked) return;
        boolean fresh = ticked == Long.MIN_VALUE;
        ticked = frame;
        game += GameTime.getInstance().getPhysicsSecondsSinceLastUpdate();
        if (fresh || dt <= 0.0f) {
            drawn = game;
        } else {
            drawn += dt;
            drawn += (game - drawn) * ThirdPersonRig.ease(dt, HOLD);
            drawn = Math.max(game - SPAN, Math.min(game + SPAN, drawn));
        }
        ahead = (float) (drawn - game);
    }

    static void stop() {
        ticked = Long.MIN_VALUE;
        ahead = 0.0f;
    }
}
