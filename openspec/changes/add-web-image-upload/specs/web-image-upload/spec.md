## ADDED Requirements

### Requirement: Browser image selection and queue
The settings web page SHALL support choosing one or multiple local image files and, when supported by the browser, a local folder whose supported images are queued recursively. The page SHALL show upload progress and a separate result for each file, and SHALL allow retrying failed files without automatically resending successful ones.

#### Scenario: Select multiple files
- **WHEN** the user chooses several supported image files and starts uploading
- **THEN** the page uploads them as independent queued files and shows each result

#### Scenario: Select a folder
- **WHEN** the user chooses a folder in a browser that supports directory selection
- **THEN** supported image files from that folder and its descendants are queued
- **AND** unsupported files are skipped with a visible skipped count

#### Scenario: Folder selection is unsupported
- **WHEN** the browser cannot select directories
- **THEN** the page still offers ordinary single and multiple file selection

### Requirement: Automatic storage card destination
The application SHALL store uploaded images in the public `Pictures/GalleryUploads/` directory on an automatically selected writable removable storage volume, display the actual destination, and preserve existing files by assigning distinct names on collision. Client directory paths SHALL NOT control the destination path.

#### Scenario: Upload to the mounted card
- **WHEN** a writable removable storage volume is available and a valid upload completes
- **THEN** the image is saved in that volume's public upload directory and its actual saved name is returned

#### Scenario: Existing file has the same name
- **WHEN** the selected upload name collides with an existing file
- **THEN** the new image receives a distinct filename and the existing file remains unchanged

#### Scenario: No writable card
- **WHEN** no writable removable volume is available or the application lacks the required access
- **THEN** the page reports that uploading is unavailable and does not silently store images in internal storage

### Requirement: Bounded and validated image transfer
The upload service SHALL stream each file with bounded memory, accept only actual supported JPEG, PNG, WebP, GIF or BMP images of at most 50 MiB, reject invalid filenames and cross-origin requests, and isolate upload limits from the settings API limits. Incomplete or rejected transfers SHALL NOT appear as completed images or overwrite existing media.

#### Scenario: Valid image transfer
- **WHEN** a same-origin request submits a supported image within the file size limit
- **THEN** the application writes and validates the file without buffering the entire file in memory

#### Scenario: Invalid or interrupted transfer
- **WHEN** the request contains invalid image data, exceeds the limit, loses its connection or the card becomes unavailable
- **THEN** the application rejects or fails that file, cleans its incomplete item, and preserves previously completed uploads

#### Scenario: Settings remain responsive
- **WHEN** an image upload is in progress
- **THEN** other bounded connections can still read and save the current settings

### Requirement: Immediate media discovery
The application SHALL publish each completed upload to the device media library and notify the active gallery to refresh its media query and single-image cache automatically. It SHALL coalesce bursts of notifications and keep incomplete images out of playback.

#### Scenario: New image joins the running gallery
- **WHEN** a completed image is published successfully
- **THEN** the running gallery refreshes its eligible media list promptly without restart or a manual scan
- **AND** the image becomes eligible under the existing playback selection rules

#### Scenario: Batch uploads complete
- **WHEN** several uploads finish close together
- **THEN** the gallery combines their refresh notifications instead of refreshing for every received data chunk

#### Scenario: Upload completes without an active playback view
- **WHEN** the image finishes publishing while the playback view is absent
- **THEN** no destroyed view is accessed and the next playback view discovers the saved image
