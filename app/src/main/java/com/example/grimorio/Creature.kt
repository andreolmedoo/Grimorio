package com.example.grimorio

// Dados da criatura usados pela tela de busca.
// imageUrl fica vazio ("") quando a API não informa imagem.
data class Creature(
    val name: String,
    val imageUrl: String
)
