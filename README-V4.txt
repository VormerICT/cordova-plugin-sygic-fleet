Sygic Fleet Embedded 26.0.7 - OutSystems/Cordova v4

Key v4 lifecycle change
-----------------------
Initialize now only requests permissions and prepares Sygic resources.
It does NOT create/start the Sygic fragment.

ShowForElement is the first call that creates the native Sygic container and
fragment. Sygic therefore starts at the real size of the OutSystems placeholder,
not at 1x1 or 10x10. This avoids the observed native libAura.so crash in
CResources::ResetSize / LowFontDelete.

Recommended screen flow
-----------------------
1. Render a visible placeholder with a stable size, e.g. id="sygic-map".
2. RegisterEventBridge.
3. Initialize. Wait for its success (status=resources_ready).
4. ShowForElement("sygic-map"). This creates/starts Sygic.
5. Wait for EVENT_APP_STARTED before calling Sygic API actions.

Important
---------
- Placeholder must be laid out and at least 32x32 physical pixels. In practice,
  give it a normal map size (for example width 100%, height 400px or more).
- Hide no longer resizes Sygic to 1x1. It uses View.INVISIBLE and preserves the
  last valid dimensions.
- Call ShowForElement again after orientation/layout changes to reposition/resize.
- Do not call Hide between Initialize and the first ShowForElement.
