Sygic Fleet Embedded 26.0.7 - OutSystems plugin v4.1

Changes from v4:
- ShowForElement now tracks the target DOM element while the page scrolls.
- Scroll tracking also catches nested OutSystems scroll containers.
- Position updates are throttled with requestAnimationFrame.
- New internal Cordova action: updatePosition.
- Native Sygic fragment is NOT recreated/restarted while scrolling.
- Negative top/left positions are allowed so Android can clip the native view at screen edges.
- Hide stops DOM tracking and keeps the last safe Aura dimensions.

OutSystems API is unchanged. Continue using ShowForElement(elementId) and Hide().
