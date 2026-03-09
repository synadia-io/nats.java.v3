// Copyright 2020 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.synadia.examples.z;

public class ZScratch0 {
    enum Foo { BAR, baz, Boo }

    public static void main(String[] args) {
        System.out.println(Integer.toHexString(222));
        System.out.println(Foo.BAR.toString());
        System.out.println(String.valueOf(Foo.baz));
        System.out.println(Foo.Boo);
        System.out.println(Foo.valueOf("BAR"));
        System.out.println(Foo.valueOf("bar".toUpperCase()));
    }
}
