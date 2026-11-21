package dev.tee.stateditor.client;

/** Viewport for the selected Pokémon model. */
record PreviewArea(int x, int y, int width, int height) {
    int centeredX(int contentWidth) {
        return x + (width - contentWidth) / 2;
    }

    int centeredY(int contentHeight) {
        return y + (height - contentHeight) / 2;
    }
}
