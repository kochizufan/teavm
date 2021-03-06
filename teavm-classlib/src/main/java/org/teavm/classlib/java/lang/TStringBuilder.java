/*
 *  Copyright 2013 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.java.lang;

/**
 *
 * @author Alexey Andreev
 */
public class TStringBuilder extends TAbstractStringBuilder {
    public TStringBuilder(int capacity) {
        super(capacity);
    }

    public TStringBuilder() {
        super();
    }

    @Override
    public TStringBuilder append(TString string) {
        super.append(string);
        return this;
    }

    @Override
    public TStringBuilder append(int value) {
        super.append(value);
        return this;
    }

    @Override
    public TStringBuilder append(long value) {
        super.append(value);
        return this;
    }

    @Override
    public TStringBuilder append(float value) {
        super.append(value);
        return this;
    }

    @Override
    protected TStringBuilder append(double value) {
        super.append(value);
        return this;
    }

    @Override
    public TStringBuilder append(char c) {
        super.append(c);
        return this;
    }

    @Override
    public TStringBuilder append(char[] chars, int offset, int len) {
        super.append(chars, offset, len);
        return this;
    }

    @Override
    public TStringBuilder appendCodePoint(int codePoint) {
        super.appendCodePoint(codePoint);
        return this;
    }

    @Override
    public TStringBuilder append(TCharSequence s, int start, int end) {
        super.append(s, start, end);
        return this;
    }

    @Override
    public TStringBuilder append(TCharSequence s) {
        super.append(s);
        return this;
    }

    @Override
    public TStringBuilder append(TObject obj) {
        super.append(obj);
        return this;
    }
}
