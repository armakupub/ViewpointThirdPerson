package pzmod.viewpointthirdperson;

import zombie.characters.CharacterInputBindingSet;
import zombie.characters.CharacterJoypadButtonBinding;
import zombie.input.JoypadAxis1d;
import zombie.input.JoypadButton;

// The game keeps controller presets as files in Zomboid/InputBindings and offers each in its
// controller options. This one is the Default with aiming on LT, so RT only attacks and the
// right stick is free to turn the camera; shove moves to LB and rack to D-pad up. Written once
// when missing, through the game's own sets, then the player's bindings are put back as they were.
public class ControllerPreset {
    static final String NAME = "Viewpoint Third Person";
    static final String DESCRIPTION = "Default layout with aim on LT, shove on LB, rack on D-pad Up. RT attacks, the right stick turns the camera.";

    public static boolean install() {
        try {
            if (CharacterInputBindingSet.containsSetName(NAME)) return false;
            CharacterInputBindingSet current = new CharacterInputBindingSet();
            current.setBindingsToCurrent();
            boolean saved;
            try {
                CharacterInputBindingSet.resetAllToDefault();
                CharacterJoypadButtonBinding.PrecisionAim.setBinding(JoypadAxis1d.LeftTrigger, -0.8f);
                CharacterJoypadButtonBinding.Melee.setBinding(JoypadButton.LeftBump);
                CharacterJoypadButtonBinding.RackFirearm.setBinding(JoypadButton.DPadUp);
                CharacterInputBindingSet set = new CharacterInputBindingSet();
                set.name = NAME;
                set.description = DESCRIPTION;
                set.setBindingsToCurrent();
                saved = set.save();
            } finally {
                current.apply();
            }
            if (!saved) return false;
            CharacterInputBindingSet.reloadAll();
            System.out.println("[ViewpointThirdPerson] controller preset \"" + NAME + "\" added");
            return true;
        } catch (RuntimeException e) {
            System.out.println("[ViewpointThirdPerson] controller preset not added: " + e);
            return false;
        }
    }
}
