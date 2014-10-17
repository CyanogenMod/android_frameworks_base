for filename in *.png; do 
    [ -f "$filename" ] || continue
    mv $filename ${filename//zz_moto_/}

done
