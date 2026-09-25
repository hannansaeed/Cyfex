#!/bin/bash
set -e

# Find raw image uploaded to project
RAW_FILE=$(find . / -maxdepth 2 -type f \( -iname "*whatsapp*" -o -iname "*logo*" -o -iname "*.jpeg" -o -iname "*.jpg" -o -iname "*.png" \) ! -path "*/app/src/*" ! -path "*/build/*" ! -path "*/.gradle/*" 2>/dev/null | head -n 1)

if [ -n "$RAW_FILE" ] && [ -f "$RAW_FILE" ]; then
    echo "Found raw image: $RAW_FILE"
    cp "$RAW_FILE" "app/src/main/res/drawable/ic_cyfex_logo.jpg"
    
    BASE="app/src/main/res"
    for spec in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
        dir="$BASE/mipmap-${spec%:*}"
        size="${spec#*:}"
        convert "$RAW_FILE" -resize "${size}x${size}!" "PNG32:$dir/ic_launcher.png"
        radius=$((size / 2))
        convert "$RAW_FILE" -resize "${size}x${size}!" \
            \( -size "${size}x${size}" xc:none -fill white -draw "circle $radius,$radius $radius,0" \) \
            -alpha set -compose DstIn -composite "PNG32:$dir/ic_launcher_round.png"
    done
    echo "Raw logo synchronized."
fi
