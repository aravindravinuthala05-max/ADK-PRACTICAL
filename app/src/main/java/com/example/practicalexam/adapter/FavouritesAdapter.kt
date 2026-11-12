package com.example.practicalexam.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.practicalexam.databinding.ItemFavouriteCityBinding
import com.example.practicalexam.model.City

class FavouritesAdapter(
    private var cities: List<City> = emptyList(),
    private val onSelectCity: (City) -> Unit,
    private val onRemoveCity: (City) -> Unit
) : RecyclerView.Adapter<FavouritesAdapter.FavouriteViewHolder>() {

    inner class FavouriteViewHolder(val binding: ItemFavouriteCityBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavouriteViewHolder {
        val binding = ItemFavouriteCityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FavouriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavouriteViewHolder, position: Int) {
        val city = cities[position]
        with(holder.binding) {
            tvFavCityName.text = city.name
            root.setOnClickListener { onSelectCity(city) }
            btnRemoveFav.setOnClickListener { onRemoveCity(city) }
        }
    }

    override fun getItemCount(): Int = cities.size

    fun updateData(newCities: List<City>) {
        this.cities = newCities
        notifyDataSetChanged()
    }
}