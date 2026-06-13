// Package crypto 提供敏感字段的对称加密（P3-6）：chat_model.api_key 等落库前加密。
// AES-256-GCM，密钥由配置 security.secret_key 经 SHA-256 归一化为 32 字节。
package crypto

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"io"
	"strings"
)

// 密文前缀标记：区分已加密值与历史明文，使解密对明文向后兼容（原样返回）
const cipherPrefix = "enc:v1:"

var gcm cipher.AEAD

// Init 用配置密钥初始化 AEAD；secret 任意长度，经 SHA-256 派生 32 字节 AES 密钥。
func Init(secret string) error {
	sum := sha256.Sum256([]byte(secret))
	block, err := aes.NewCipher(sum[:])
	if err != nil {
		return err
	}
	gcm, err = cipher.NewGCM(block)
	return err
}

// Encrypt 加密明文，输出 "enc:v1:" + base64(nonce|ciphertext)。空串原样返回。
func Encrypt(plain string) (string, error) {
	if plain == "" || gcm == nil {
		return plain, nil
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return "", err
	}
	sealed := gcm.Seal(nonce, nonce, []byte(plain), nil)
	return cipherPrefix + base64.StdEncoding.EncodeToString(sealed), nil
}

// EncryptIfNeeded 仅在尚未加密时加密，避免重复加密（如 Update 回填已加密值）。
func EncryptIfNeeded(s string) string {
	if s == "" || strings.HasPrefix(s, cipherPrefix) {
		return s
	}
	enc, err := Encrypt(s)
	if err != nil {
		return s
	}
	return enc
}

// Decrypt 解密；非本格式（历史明文）原样返回，实现平滑迁移。
func Decrypt(s string) string {
	if !strings.HasPrefix(s, cipherPrefix) || gcm == nil {
		return s
	}
	raw, err := base64.StdEncoding.DecodeString(strings.TrimPrefix(s, cipherPrefix))
	if err != nil || len(raw) < gcm.NonceSize() {
		return s
	}
	nonce, ct := raw[:gcm.NonceSize()], raw[gcm.NonceSize():]
	plain, err := gcm.Open(nil, nonce, ct, nil)
	if err != nil {
		return s
	}
	return string(plain)
}

// ErrNotInit 在未初始化时由调用方按需使用
var ErrNotInit = errors.New("crypto 未初始化")
