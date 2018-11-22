package org.librespot.spotify;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Gianlu
 */
public class BytesArrayList implements Iterable<byte[]> {
    private byte[][] elementData;
    private int size;

    public BytesArrayList() {
        size = 0;
        elementData = new byte[5][];
    }

    private BytesArrayList(byte[][] buffer) {
        elementData = buffer;
        size = buffer.length;
    }

    private void ensureExplicitCapacity(int minCapacity) {
        if (minCapacity - elementData.length > 0)
            grow(minCapacity);
    }

    public void add(byte[] e) {
        ensureExplicitCapacity(size + 1);
        elementData[size++] = e;
    }

    public byte[] get(int index) {
        if (index >= size) throw new IndexOutOfBoundsException(String.format("size: %d, index: %d", size, index));
        return elementData[index];
    }

    public byte[][] toArray() {
        return Arrays.copyOfRange(elementData, 0, size);
    }

    private void grow(int minCapacity) {
        int oldCapacity = elementData.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity - minCapacity < 0) newCapacity = minCapacity;
        elementData = Arrays.copyOf(elementData, newCapacity);
    }

    @NotNull
    public BytesArrayList copyOfRange(int from, int to) {
        return new BytesArrayList(Arrays.copyOfRange(elementData, from, to));
    }

    public int size() {
        return size;
    }

    @NotNull
    @Override
    public Iterator<byte[]> iterator() {
        return new Itr();
    }

    @Override
    public String toString() {
        return Arrays.deepToString(toArray());
    }

    private class Itr implements Iterator<byte[]> {
        int cursor = 0;

        @Override
        public boolean hasNext() {
            return cursor != size();
        }

        @Override
        public byte[] next() {
            try {
                int i = cursor;
                byte[] next = get(i);
                cursor = i + 1;
                return next;
            } catch (IndexOutOfBoundsException ex) {
                throw new NoSuchElementException();
            }
        }
    }
}