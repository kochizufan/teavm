/*
 *  Copyright 2012 Alexey Andreev.
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
package org.teavm.codegen;

import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;

/**
 *
 * @author Alexey Andreev
 */
public interface AliasProvider {
    String getAlias(FieldReference field);

    String getAlias(MethodReference method);

    String getAlias(String className);
}
