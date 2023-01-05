package com.pbeagan.data

import com.pbeagan.actions.RoomTile
import com.pbeagan.consolevision.Coord

import com.pbeagan.util.FlagSet
import com.pbeagan.consolevision.List2D
import mobs

class RoomData constructor(
    val id: Int,
    val name: String,
    override val descriptionLook: String,
    override val descriptionPreview: (Direction) -> String,
    var exits: Exits,
    var roomFlags: FlagSet<RoomFlags> = FlagSet.of(),
    var weather: Weather = Weather.CLEAR,
    var lighting: Lighting = Lighting.BRIGHT,
    var items: MutableCollection<ItemData> = mutableListOf(),
    terrainString: String,
) : Lookable {
    val terrain: List2D<Terrain> = TerrainParser.parse(terrainString)

    fun getLocation(xy: Coord) = RoomTile(
        terrain.at(xy.x, xy.y),
        mobs.filter { it.location == id && it.locationInRoom == xy },
        items.filter { it.locationInRoom == xy }
    )
}