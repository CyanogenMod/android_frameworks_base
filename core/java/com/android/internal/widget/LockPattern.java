package com.android.internal.widget;

import java.util.List;

import android.view.View;

public interface LockPattern {
    public static class Cell {
        int row;
        int column;

        // keep # objects limited to 9
        static Cell[][] sCells = new Cell[3][3];
        static {
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    sCells[i][j] = new Cell(i, j);
                }
            }
        }

        /**
         * @param row The row of the cell.
         * @param column The column of the cell.
         */
        private Cell(int row, int column) {
            checkRange(row, column);
            this.row = row;
            this.column = column;
        }

        public int getRow() {
            return row;
        }

        public int getColumn() {
            return column;
        }

        /**
         * @param row The row of the cell.
         * @param column The column of the cell.
         */
        public static synchronized Cell of(int row, int column) {
            checkRange(row, column);
            return sCells[row][column];
        }

        private static void checkRange(int row, int column) {
            if (row < 0 || row > 2) {
                throw new IllegalArgumentException("row must be in range 0-2");
            }
            if (column < 0 || column > 2) {
                throw new IllegalArgumentException("column must be in range 0-2");
            }
        }

        public String toString() {
            return "(row=" + row + ",clmn=" + column + ")";
        }
    }

    /**
     * How to display the current pattern.
     */
    public enum State {
        Record,
        Replay,
        Incorrect,
        Correct
    }

    /**
     * The call back interface for detecting patterns entered by the user.
     */
    public static interface EventListener {
        void onPatternStart();
        void onPatternCleared();
        void onUserInteraction();
        void onPatternDetected(List<Cell> pattern);
    }

    public boolean isTactileFeedbackEnabled();
    public void setTactileFeedbackEnabled(boolean tactileFeedbackEnabled);
    public boolean isInStealthMode();
    public void setInStealthMode(boolean inStealthMode);
    public void setVisibleDots(boolean visibleDots);
    public boolean isVisibleDots();
    public void setShowErrorPath(boolean showErrorPath);
    public boolean isShowErrorPath();    
    public void setEventListener(EventListener eventListener);
    public void setPattern(State state, List<Cell> pattern);
    public void setState(State state);
    public void clearPattern();
    public void disableInput();
    public void enableInput();
    public int getCorrectDelay();
    public int getIncorrectDelay();
    public void setIncorrectDelay(int delay);
    public View getView();
}
