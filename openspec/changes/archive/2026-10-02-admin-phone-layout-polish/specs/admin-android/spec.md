## MODIFIED Requirements

### Requirement: Images from the photo library or the camera
On Android, "choose images" SHALL open the system photo picker with multiple selection, and "take
photo" SHALL open the camera. The app SHALL NOT request permission to read all photos or files.
Chosen and taken images SHALL be uploaded as in the web app. A chosen or taken image whose bytes are
not JPEG, PNG or WebP (for example HEIC/HEIF) SHALL be converted to JPEG on the device before
upload. The conversion SHALL apply the photo's orientation, and the converted file SHALL keep the
original name with the extension `.jpg`. JPEG, PNG and WebP images SHALL be uploaded unchanged. An
image the device cannot decode SHALL be uploaded unchanged, so that it fails with the usual "not a
supported image type" message.

#### Scenario: Upload from the gallery
- **WHEN** a reporter chooses two photos in the photo picker
- **THEN** both are uploaded and appear in "Images"

#### Scenario: Photo taken with the camera
- **WHEN** a reporter takes a photo with "take photo"
- **THEN** it is uploaded and appears in "Images"

#### Scenario: HEIC photo from the gallery
- **WHEN** a reporter chooses `IMG_0042.heic`, taken in portrait orientation, in the photo picker
- **THEN** the upload dialog lists `IMG_0042.jpg`, the upload succeeds, and the image appears in "Images" upright

#### Scenario: JPEG stays untouched
- **WHEN** a reporter chooses a JPEG photo
- **THEN** its bytes are uploaded unchanged

#### Scenario: Undecodable file
- **WHEN** a reporter chooses an image the device cannot decode
- **THEN** the upload fails for that file with "not a supported image type", and the other chosen files are uploaded
