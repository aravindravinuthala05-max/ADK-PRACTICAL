package com.example.practicalexam.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.practicalexam.databinding.ItemForecastDayBinding
import com.example.practicalexam.model.DailyForecast
import com.example.practicalexam.util.WeatherUtils

class ForecastAdapter(
    private var items: List<DailyForecast> = emptyList(),
    private var isCelsius: Boolean = true
) : RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder>() {

    inner class ForecastViewHolder(val binding: ItemForecastDayBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForecastViewHolder {
        val binding = ItemForecastDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ForecastViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ForecastViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvForecastDay.text = WeatherUtils.formatDayOfWeek(item.dateString)
            tvForecastDate.text = WeatherUtils.formatDateShort(item.dateString)
            ivForecastIcon.setImageResource(WeatherUtils.getWeatherIconResId(item.weatherCode))
            tvForecastTempMax.text = WeatherUtils.formatTemperature(item.tempMax, isCelsius)
            tvForecastTempMin.text = WeatherUtils.formatTemperature(item.tempMin, isCelsius)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<DailyForecast>, celsiusUnit: Boolean) {
        this.items = newItems
        this.isCelsius = celsiusUnit
        notifyDataSetChanged()
    }

    fun updateUnit(celsiusUnit: Boolean) {
        this.isCelsius = celsiusUnit
        notifyDataSetChanged()
    }
}