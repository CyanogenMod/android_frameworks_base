
package com.vanir.objects;

import android.util.Pair;

import java.util.Arrays;

public class EasyPair<Ta, Tb> extends Pair<Ta, Tb> {
    /**
     * Constructor for a Pair. If either are null then equals() and hashCode() will throw
     * a NullPointerException.
     *
     * @param first  the first object in the Pair
     * @param second the second object in the pair
     */
    public Ta first;
    public Tb second;
    public EasyPair(Ta first, Tb second) {
        super(first, second);
        this.first = first;
        this.second = second;
    }

    /**
     * Constructs a new EasyPair from an existing EasyPair
     * @param pair
     */
    public EasyPair(EasyPair<Ta, Tb> pair) {
        super(pair.getFirst(), pair.getSecond());
        this.first = pair.getFirst();
        this.second = pair.getSecond();
    }

    @Override
    public String toString() {
        String first_;
        String second_;

        try {
            first_ = Arrays.toString((Ta[]) first);
        } catch (ClassCastException badCast) {
            first_ = first.toString();
        }

        try {
            second_ = Arrays.toString((Tb[]) second);
        } catch (ClassCastException badCast) {
            second_ = second.toString();
        }
        return "EasyPair<" + first_ + ", " + second_ + ">";
    }

    public Ta getFirst() {
        return first;
    }

    public Tb getSecond() {
        return second;
    }

    public void changeFirst(Ta newFirst) {
        this.first = newFirst;
    }

    public void changeSecond(Tb newSecond) {
        this.second = newSecond;
    }

    public EasyPair<Ta, Tb> clonePair() {
        return new EasyPair<Ta, Tb>(first, second);
    }

    public boolean equals() {
        // yea they equal but not in a good way
        if (first == null || second == null)
            return false;
        // return a method that won't throw null
        return first == second;
    }
}

