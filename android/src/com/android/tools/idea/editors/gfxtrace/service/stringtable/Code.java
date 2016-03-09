/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service.stringtable;

import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.*;
import com.android.tools.rpclib.schema.*;

import java.io.IOException;

public final class Code extends Node implements BinaryObject {
  @Override
  public String getString(java.util.Map<String, BinaryObject> arguments) {
    return myBody.getString(arguments);
  }

  //<<<Start:Java.ClassBody:1>>>
  private Node myBody;
  private String myLanguage;

  // Constructs a default-initialized {@link Code}.
  public Code() {}


  public Node getBody() {
    return myBody;
  }

  public Code setBody(Node v) {
    myBody = v;
    return this;
  }

  public String getLanguage() {
    return myLanguage;
  }

  public Code setLanguage(String v) {
    myLanguage = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("stringtable", "Code", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("Body", new Interface("Node")),
      new Field("Language", new Primitive("string", Method.String)),
    });
    Namespace.register(Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

    @Override @NotNull
    public BinaryObject create() { return new Code(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      Code o = (Code)obj;
      e.object(o.myBody.unwrap());
      e.string(o.myLanguage);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      Code o = (Code)obj;
      o.myBody = Node.wrap(d.object());
      o.myLanguage = d.string();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}