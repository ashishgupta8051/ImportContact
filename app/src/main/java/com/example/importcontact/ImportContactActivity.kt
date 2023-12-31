package com.example.importcontact

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.RawContacts
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.importcontact.adapter.ContactAdapter
import com.example.importcontact.databinding.ActivityImportContactBinding
import com.example.importcontact.model.Contact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.util.concurrent.Executors


class ImportContactActivity : AppCompatActivity(), OnClickListener {
    private lateinit var binding: ActivityImportContactBinding
    private val adapter: ContactAdapter = ContactAdapter()
    private val threadPool = Executors.newFixedThreadPool(4).asCoroutineDispatcher()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportContactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnFetchContact.setOnClickListener(this)
        binding.btnImportContact.setOnClickListener(this)
        binding.btnDeleteContact.setOnClickListener(this)

    }

    private fun checkPermission(check: Int) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
                1001
            )
        } else {
            when (check) {
                1 -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val count = readVCFFromAssets()
                        withContext(Dispatchers.Main) {
                            if (count == 0) {
                                Toast.makeText(
                                    this@ImportContactActivity,
                                    "Something is wrong",
                                    Toast.LENGTH_SHORT
                                ).show()
                                hideProgress()
                                binding.btnImportContact.isEnabled = true
                                setAdapter()
                                threadPool.close()
                            } else {
                                hideProgress()
                                Toast.makeText(
                                    this@ImportContactActivity,
                                    "Contact imported successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                                binding.btnImportContact.isEnabled = true
                                setAdapter()
                                threadPool.close()
                            }
                        }
                    }
                }

                2 -> {
                    setAdapter()
                }

                3 -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        if (deleteAllContacts()) {
                            withContext(Dispatchers.Main) {
                                binding.btnDeleteContact.isEnabled = true
                                setAdapter()
                                Toast.makeText(
                                    this@ImportContactActivity,
                                    "Deleted Successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                                hideProgress()
                                threadPool.close()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                binding.btnDeleteContact.isEnabled = true
                                hideProgress()
                                Toast.makeText(this@ImportContactActivity,
                                    "Something is wrong",
                                    Toast.LENGTH_SHORT
                                ).show()
                                threadPool.close()
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
    private fun setAdapter() {
        CoroutineScope(Dispatchers.IO).launch {
            val contactList = retrieveAllContacts()
            withContext(Dispatchers.Main) {
                if (contactList.isEmpty()) {
                    hideProgress()
                    binding.tvContactTotalSize.text = "Total Size: 0"
                    adapter.getContactList(contactList)
                    binding.btnFetchContact.isEnabled = true
                } else {
                    hideProgress()
                    adapter.getContactList(contactList)
                    binding.rvContact.adapter = adapter
                    binding.tvContactTotalSize.text = "Total Size: ${contactList.size}"
                    binding.btnFetchContact.isEnabled = true
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun readVCFFromAssets(): Int {
        var count = 0
        try {
            withContext(Dispatchers.Main) {
                showProgress()
                binding.btnImportContact.isEnabled = false
            }
            val assetManager = assets
//            val fileName = "contacts_2_nfr_50K_1L.vcf" // Put your VCF file name here
            val fileName = "contacts_2_nfr_1L_1K.vcf"
            val inputStream = assetManager.open(fileName)
            val reader = inputStream.bufferedReader()

            var isPhoneNumber = false
            var phoneNumber: String? = null
            while (withContext(Dispatchers.IO) {
                    reader.readLine()
                }?.also { line ->
//                    Log.d("VCFData", line)
                    if (line.startsWith("FN")) {
                        phoneNumber = line.substring(3)
                        isPhoneNumber = true
                        importContactNumber(phoneNumber ?: "N/A", phoneNumber ?: "N/A")
                    } else if (isPhoneNumber) {
                        count += 1
                        withContext(Dispatchers.Main) {
                            binding.tvContactTotalSize.text = "Contact Imported: $count"
                        }
                        Log.d("ContactNumber", ("$phoneNumber Count:$count"))
                        isPhoneNumber = false
                        phoneNumber = null
                    }
                } != null) {
            }

            withContext(Dispatchers.IO) {
                reader.close()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return count
    }

    @SuppressLint("SetTextI18n")
    private fun importContactNumber(phoneNumber: String, contactName: String) {
        runBlocking {
            val contentResolver: ContentResolver = contentResolver
            val operations = ArrayList<ContentProviderOperation>()
            val rawContactUri = contentResolver.insert(RawContacts.CONTENT_URI, ContentValues()) ?: return@runBlocking
            val rawContactId = ContentUris.parseId(rawContactUri)

            // Insert the contact name if available
            val nameValues = ContentValues()
            nameValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            nameValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            nameValues.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contactName)
            val nameOperation = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValues(nameValues).build()
            operations.add(nameOperation)

            // Insert the contact number
            val phoneValues = ContentValues()
            phoneValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            phoneValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            phoneValues.put(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
            phoneValues.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            val phoneOperation = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValues(phoneValues).build()
            operations.add(phoneOperation)


            launch(threadPool) {
                    contentResolver.applyBatch(ContactsContract.AUTHORITY,operations)
                    Log.e("Thread: ","${Thread.currentThread().name}, Number: $phoneNumber")
            }
        }
    }

    @SuppressLint("Range")
    suspend fun retrieveAllContacts(): List<Contact> {
        withContext(Dispatchers.Main) {
            binding.btnFetchContact.isEnabled = false
            showProgress()
        }
        val contactsList = mutableListOf<Contact>()
        val contentResolver: ContentResolver = contentResolver

        // Define the columns you want to retrieve
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        // Perform the query
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null, // Selection (null retrieves all contacts)
            null, // SelectionArgs
            null  // Sort order
        )

        contactsList.clear()
        cursor?.use {
            while (it.moveToNext()) {
//                val contactId = it.getLong(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID))
                val name =
                    it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val phoneNumber =
                    it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))

                val contact = Contact(name, phoneNumber)
                contactsList.add(contact)
            }
        }
        return contactsList
    }

    @SuppressLint("Range")
    suspend fun deleteAllContacts(): Boolean {
        runBlocking {
            withContext(Dispatchers.Main) {
                binding.btnDeleteContact.isEnabled = false
                showProgress()
            }
            val contentResolver: ContentResolver = contentResolver
            // Get the list of all contact IDs
            val contactIds = mutableListOf<Long>()
            val cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                null,
                null,
                null,
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val contactId = it.getLong(it.getColumnIndex(ContactsContract.Contacts._ID))
                    contactIds.add(contactId)
                }
            }

            cursor?.close()

            // Delete each contact individually
            for (contactId in contactIds) {
                val deleteUri = RawContacts.CONTENT_URI.buildUpon()
                    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                    .build()

                val ops = arrayListOf<ContentProviderOperation>()
                ops.add(
                    ContentProviderOperation.newDelete(deleteUri)
                        .withSelection(RawContacts.CONTACT_ID + "=?", arrayOf(contactId.toString()))
                        .build()
                )

                try {
                    launch(threadPool) {
                        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                        Log.e("Thread: ", Thread.currentThread().name)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return true
    }

    @SuppressLint("SetTextI18n")
    override fun onClick(view: View?) {
        if (view != null) {
            when (view.id) {
                R.id.btnFetchContact -> {
                    checkPermission(2)
                }

                R.id.btnImportContact -> {
                    checkPermission(1)
                }

                R.id.btnDeleteContact -> {
                    checkPermission(3)
                }
            }
        }
    }

    private fun showProgress() {
        binding.progresssBar.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        binding.progresssBar.visibility = View.GONE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1001 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Contact permission granted", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, " Contact permission not granted", Toast.LENGTH_LONG)
                        .show()
                    // Permission denied, handle it accordingly (e.g., show a message or request the permission again)
                }
            }
        }
    }

}