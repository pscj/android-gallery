## MODIFIED Requirements

### Requirement: Random image response
The service SHALL provide GET `/api/images/random` on the existing HTTP server and uniformly select one readable, published MediaStore image matching the orientation filter. The eligible collection SHALL include accessible internal and mounted removable storage images and exclude videos and invalid images. Without output dimensions, the service SHALL stream original image bytes with the original image MIME type without loading the complete image into memory. With valid output dimensions, the service SHALL return a processed JPEG as specified below. Both response modes SHALL use `Cache-Control: no-store`. Repeated requests MAY select the same image.

#### Scenario: Request without orientation
- **WHEN** a client requests `/api/images/random` without orientation or output dimensions and readable landscape images exist
- **THEN** the service defaults to landscape and returns HTTP 200 with the original bytes of one random landscape image, including square candidates

#### Scenario: Newly uploaded image
- **WHEN** an upload has been published to MediaStore
- **THEN** that image is eligible for subsequent requests if readable and matching the orientation filter

#### Scenario: Request with output dimensions
- **WHEN** a client provides valid width and height and an eligible image exists
- **THEN** the service returns HTTP 200 with a cropped JPEG of exactly those dimensions and Content-Type `image/jpeg`

## ADDED Requirements

### Requirement: Output resolution parameters
The service SHALL accept optional paired `width` and `height` query parameters in pixels. Each SHALL contain only decimal digits and represent an integer from 1 through 4096, and their product SHALL NOT exceed 8,388,608 pixels. The output resolution SHALL NOT change the existing orientation selection rule, which defaults to landscape when orientation is omitted.

#### Scenario: Default landscape with dimensions
- **WHEN** a client requests `?width=1920&height=1080`
- **THEN** the service selects from landscape images and produces a 1920 by 1080 result

#### Scenario: Portrait output
- **WHEN** a client requests `?orientation=portrait&width=1080&height=1920`
- **THEN** the service selects from portrait images and produces a 1080 by 1920 result

#### Scenario: Invalid dimensions
- **WHEN** only one dimension is supplied, a dimension is empty, repeated, non-integer, non-positive or above its limit, or the total pixel count exceeds the limit
- **THEN** the service returns HTTP 400 with a JSON error before selecting or decoding an image

### Requirement: Center crop and image encoding
The service SHALL apply EXIF rotation and mirroring, crop the image centrally to the target aspect ratio, and scale proportionally to the exact requested dimensions without distortion or padding. It SHALL allow upscaling smaller sources and encode the result as JPEG at quality 85, compositing transparent pixels over white. Animated sources SHALL produce only their first static frame. Source files SHALL remain unchanged.

#### Scenario: Different aspect ratios
- **WHEN** the source aspect ratio differs from the requested output
- **THEN** excess image content is removed symmetrically from the relevant edges and the output fills the requested dimensions

#### Scenario: Rotated or mirrored source
- **WHEN** the source contains EXIF orientation information
- **THEN** the resulting JPEG pixels reflect the corrected display orientation and require no EXIF correction by the client

### Requirement: Bounded image processing
The service SHALL use bounded decoding for the selected image and limit crop-and-encode execution to one concurrent request. It SHALL release image resources and temporary output after completion or failure. Existing original-image and settings requests SHALL remain available during processing.

#### Scenario: Concurrent crop request
- **WHEN** another crop-and-encode request is in progress
- **THEN** the service returns HTTP 429 with a JSON error for the additional crop request

#### Scenario: Conversion failure
- **WHEN** the selected image cannot be decoded, transformed or encoded before response transmission starts
- **THEN** the service returns HTTP 500 with a JSON error instead of claiming success or returning the original image
