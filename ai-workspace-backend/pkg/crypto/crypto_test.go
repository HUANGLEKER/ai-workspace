package crypto

import "testing"

func TestEncryptDecryptRoundTrip(t *testing.T) {
	if err := Init("test-secret"); err != nil {
		t.Fatalf("init: %v", err)
	}
	plain := "sk-very-secret-key-123"
	enc, err := Encrypt(plain)
	if err != nil {
		t.Fatalf("encrypt: %v", err)
	}
	if enc == plain {
		t.Fatal("密文不应等于明文")
	}
	if Decrypt(enc) != plain {
		t.Fatalf("解密不还原: %s", Decrypt(enc))
	}
}

func TestDecryptPlaintextPassthrough(t *testing.T) {
	_ = Init("k")
	// 历史明文（无 enc:v1: 前缀）应原样返回，实现平滑迁移
	if Decrypt("plain-old-key") != "plain-old-key" {
		t.Fatal("明文应原样返回")
	}
}

func TestEncryptIfNeededIdempotent(t *testing.T) {
	_ = Init("k")
	once := EncryptIfNeeded("secret")
	twice := EncryptIfNeeded(once)
	if once != twice {
		t.Fatal("已加密值不应被重复加密")
	}
	if Decrypt(twice) != "secret" {
		t.Fatalf("重复加密破坏了还原: %s", Decrypt(twice))
	}
}

func TestEmptyStays(t *testing.T) {
	_ = Init("k")
	if v, _ := Encrypt(""); v != "" {
		t.Fatal("空串加密应仍为空")
	}
	if EncryptIfNeeded("") != "" {
		t.Fatal("空串 EncryptIfNeeded 应仍为空")
	}
}
