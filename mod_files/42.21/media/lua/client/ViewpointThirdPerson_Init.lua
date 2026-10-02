-- Adds the controller preset to the game's list once, and shows it there straight away.
local function installPreset()
    if ViewpointThirdPerson and ViewpointThirdPerson.installControllerPreset() and gamepadBinding then
        gamepadBinding:populateFromLoadedSets()
    end
end
installPreset()
Events.OnMainMenuEnter.Add(installPreset)

local wrapped = false

Events.OnGameStart.Add(function()
    if not ViewpointThirdPerson then return end
    ViewpointThirdPerson.init()
    if wrapped then return end
    wrapped = true

    -- D-pad down selects in Viewpoint's loot panel while it shows, in place of the emote wheel.
    local displayDown = ISDPadWheels.onDisplayDown
    ISDPadWheels.onDisplayDown = function(joypadData)
        if joypadData.player == 0 and ViewpointThirdPerson.lootPanelPress() then return end
        return displayDown(joypadData)
    end
    -- RB takes or does what the panel selects, in place of reloading, while it shows.
    local rbPress = ISButtonPrompt.onRBPress
    ISButtonPrompt.onRBPress = function(self)
        if self.player == 0 and ViewpointThirdPerson.lootPanelTake() then return end
        return rbPress(self)
    end
    local releaseDown = JoypadControllerData.onReleaseDown
    JoypadControllerData.onReleaseDown = function(self)
        if self.joypad and self.joypad.player == 0 then ViewpointThirdPerson.lootPanelRelease() end
        return releaseDown(self)
    end
end)
