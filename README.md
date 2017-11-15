# Mobile-Steganography
Steganography Android App: Beta Version

1. Hide 324 bits with 800x800 image, using 40x40 blocks, storage can
be increased by segmentation.

2. Use hsv color model, easy to code/decode, hardly be affected by
hardware conditions, swap V value of centre/rest areas for embedding.
Change offset value to balance invisibility & performance.

3. Square edge detection and bitmap resizing by offset value which 
should be greater than background color in hsv. Best performance with 
white background (A4 paper etc.)

4. Work on the laptop screen as well, just need to modify the background
color of photoviewer to white.
