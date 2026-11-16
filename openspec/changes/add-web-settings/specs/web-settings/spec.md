## ADDED Requirements

### Requirement: Browser-accessible settings entry
The application SHALL provide an automatically started HTTP settings page on fixed port 8000 served by the Android device for browsers on the same local network, and SHALL display its current usable URL and access status in the native settings dialog.

#### Scenario: Open settings from another device
- **WHEN** the application is running and the user enters http://<device-ip>:8000/ in a browser on the same reachable local network
- **THEN** the browser displays a Chinese settings page suitable for phone and desktop screens

#### Scenario: Service cannot start
- **WHEN** the service cannot bind its port or no usable network address is available
- **THEN** the native settings dialog explains the unavailable state instead of presenting an address as ready

### Requirement: Existing setting parity
The web settings page SHALL offer all existing change intervals (10 seconds, 30 seconds, 1, 2, 5, 10, 15, 30 minutes and 1 hour) and both existing display modes (grid and single), loading their current persisted values without authentication.

#### Scenario: Read current settings
- **WHEN** a user opens or reloads the settings page
- **THEN** its controls reflect the settings currently saved on the Android device

### Requirement: Shared persistence and application
The native and web settings SHALL use the same validated settings storage and playback update path, preserve existing valid preferences, and use 5 minutes and grid mode when no preferences have been saved.

#### Scenario: Save from the browser
- **WHEN** a user saves a valid interval and display mode
- **THEN** the application persists both settings together, updates the active playback on the main thread, and the browser confirms success
- **AND** reopening native settings or restarting the application reflects the saved values

#### Scenario: Reject invalid settings
- **WHEN** a save request is malformed or contains an unsupported interval or display mode
- **THEN** the service returns an error and preserves both previously saved settings

#### Scenario: Save from native settings
- **WHEN** the user saves settings in the existing native dialog
- **THEN** playback uses the shared update path and reloading the web page shows those saved settings

### Requirement: Explicit service access and lifecycle
The web service SHALL start automatically with its owning Activity on fixed port 8000, allow settings APIs without an access secret, reject cross-origin modification requests, enforce bounded request processing, and stop when disabled or when its owning Activity is destroyed.

#### Scenario: Direct access without a password
- **WHEN** a user opens the device URL on the same local network
- **THEN** the page automatically reads current settings and allows saving without a login or password

#### Scenario: Disable web settings
- **WHEN** the user disables the service or its owning Activity is destroyed
- **THEN** the service releases its listening socket and no longer accepts settings requests

#### Scenario: Request fails in the browser
- **WHEN** the service becomes unreachable or a save request fails
- **THEN** the page shows a clear failure message without claiming that settings were saved
