## ADDED Requirements

### Requirement: Random image response
The service SHALL provide GET `/api/images/random` on the existing HTTP server and return the original bytes of one uniformly selected readable, published MediaStore image with its image MIME type and `Cache-Control: no-store`. The eligible collection SHALL include accessible internal and mounted removable storage images and exclude videos and invalid images. The response SHALL stream image bytes without loading the complete image into memory. Repeated requests MAY select the same image.

#### Scenario: Request without orientation
- **WHEN** a client requests `/api/images/random` without an orientation parameter and readable landscape images exist
- **THEN** the service defaults to the landscape filter and returns HTTP 200 with one random landscape image, including square images as eligible candidates

#### Scenario: Newly uploaded image
- **WHEN** an upload has been published to MediaStore
- **THEN** that image is eligible for subsequent random image requests if readable and matching the requested orientation

### Requirement: Orientation filtering
The service SHALL accept the optional query parameter `orientation` with exactly the values `portrait` and `landscape`, defaulting to `landscape` when omitted. It SHALL classify images using their dimensions after applying EXIF rotation: height greater than width is portrait, and width greater than or equal to height is landscape.

#### Scenario: Portrait filter
- **WHEN** a client requests `/api/images/random?orientation=portrait`
- **THEN** only images whose displayed height exceeds their displayed width are eligible

#### Scenario: Landscape filter
- **WHEN** a client requests `/api/images/random?orientation=landscape`
- **THEN** only images whose displayed width is at least their displayed height are eligible

#### Scenario: Invalid orientation
- **WHEN** orientation is explicitly empty, repeated, or different from the two supported values
- **THEN** the service returns HTTP 400 with a JSON error instead of silently ignoring the filter

### Requirement: Errors and existing service integration
The endpoint SHALL reuse the existing server lifecycle and access checks, return JSON errors for unsuccessful requests, and preserve existing settings and upload API behavior.

#### Scenario: No eligible image
- **WHEN** no readable published image matches the requested filter
- **THEN** the service returns HTTP 404 with a JSON error

#### Scenario: Missing read permission
- **WHEN** the application lacks the permission needed to read the image collection
- **THEN** the service returns HTTP 403 with a JSON error

#### Scenario: Media query failure
- **WHEN** a server-side media query fails
- **THEN** the service returns HTTP 500 with a JSON error

#### Scenario: Unsupported method
- **WHEN** the endpoint receives a method other than GET
- **THEN** the service returns HTTP 405 with a JSON error

#### Scenario: Service stops
- **WHEN** the existing web service is disabled or its owning Activity is destroyed
- **THEN** the random image endpoint stops accepting connections along with the other endpoints
