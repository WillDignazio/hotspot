/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef SHARE_VM_MEMORY_CARDTABLEMODREFBS_INLINE_HPP
#define SHARE_VM_MEMORY_CARDTABLEMODREFBS_INLINE_HPP

#include "memory/cardTableModRefBS.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/orderAccess.inline.hpp"

template <class T> inline void CardTableModRefBS::inline_write_ref_field(T* field, oop newVal, bool release) {
  jbyte* byte = byte_for((void*)field);
  if (release) {
    // Perform a releasing store if requested.
    OrderAccess::release_store((volatile jbyte*) byte, dirty_card);
  } else {
    *byte = dirty_card;
  }
}

#endif // SHARE_VM_MEMORY_CARDTABLEMODREFBS_INLINE_HPP
