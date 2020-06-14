package com.warburg.example;

/**
 * @author ewarburg
 */
public class Example {
    private Example() {}

    static class Option extends Example {
        private final String field1;
        private final int field2;

        public Option(final String field1, final int field2) {
            this.field1 = field1;
            this.field2 = field2;
        }

        public String getField1() {
            return this.field1;
        }

        public int getField2() {
            return this.field2;
        }
    }
}
