# Real-hand test fixtures

`hagrid_landmarks.csv.gz` (220 hands per class, used to choose thresholds) and
`hagrid_holdout.csv.gz` (100 different hands per class, never used for tuning) hold MediaPipe
landmarks, not images. They were made by cropping the labeled gesture hand from photos in the
HaGRID sample set (`cj-mills/hagrid-sample-30k-384p` on Hugging Face) and running the
`hand_landmarker.task` model shipped in this library on each crop.

HaGRID is published by SberDevices under CC BY-SA 4.0
(https://github.com/hukenovs/hagrid). These derived landmark files are shared under the same
license.

Each line: `label,width,height,handedness`, then 63 normalized image values (x, y, z for 21
landmarks), then 63 world values in meters.
