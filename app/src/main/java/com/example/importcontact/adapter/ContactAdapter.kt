package com.example.importcontact.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.example.importcontact.R
import com.example.importcontact.databinding.ContactItemBinding
import com.example.importcontact.model.Contact

class ContactAdapter() : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {
    private val contacts: MutableList<Contact> = mutableListOf()

    @SuppressLint("NotifyDataSetChanged")
    fun getContactList(list:List<Contact>){
        contacts.clear()
        contacts.addAll(list)
        notifyDataSetChanged()
    }

    inner class ContactViewHolder(val binding: ContactItemBinding) : ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ContactItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        val biding = holder.binding
        biding.name.text = contact.name
        biding.phone.text = contact.phoneNumber
        val count = position + 1
        biding.count.text = "$count."
    }

    override fun getItemCount(): Int {
        return contacts.size
    }
}

