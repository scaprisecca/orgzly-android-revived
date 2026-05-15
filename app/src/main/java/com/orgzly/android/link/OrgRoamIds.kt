package com.orgzly.android.link

import java.util.UUID

object OrgRoamIds {
    fun newId(): String = UUID.randomUUID().toString()
}
