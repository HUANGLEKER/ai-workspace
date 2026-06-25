package config

import (
	"fmt"
	"strings"

	"github.com/spf13/viper"
)

type Config struct {
	Server    ServerConfig    `mapstructure:"server"`
	Database  DatabaseConfig  `mapstructure:"database"`
	Redis     RedisConfig     `mapstructure:"redis"`
	JWT       JWTConfig       `mapstructure:"jwt"`
	FastAPI   FastAPIConfig   `mapstructure:"fastapi"`
	MinIO     MinIOConfig     `mapstructure:"minio"`
	Log       LogConfig       `mapstructure:"log"`
	RateLimit RateLimitConfig `mapstructure:"ratelimit"`
	Upload    UploadConfig    `mapstructure:"upload"`
	Security  SecurityConfig  `mapstructure:"security"`
	CORS      CORSConfig      `mapstructure:"cors"`
	Tracing   TracingConfig   `mapstructure:"tracing"`
}

// TracingConfig 控制 OpenTelemetry 分布式链路追踪（全链路可观测性）。
type TracingConfig struct {
	// ServiceName 在 APM 后端中标识本服务；为空时回退 "ai-workspace-backend"
	ServiceName string `mapstructure:"service_name"`
	// Endpoint 为 OTLP/HTTP 采集端点（host:port，如 localhost:4318）；为空则不启用追踪
	Endpoint string `mapstructure:"endpoint"`
}

type SecurityConfig struct {
	// 敏感字段（chat_model.api_key 等）落库加密的密钥；生产务必改并保密
	SecretKey string `mapstructure:"secret_key"`
	// 是否启用登录滑块拼图验证；关闭则登录跳过验证码（便于本地/测试）
	CaptchaEnabled bool `mapstructure:"captcha_enabled"`
}

type CORSConfig struct {
	// 允许的跨域来源列表；含 "*" 或为空时放通所有来源（仅本地/单用户安全）
	AllowedOrigins []string `mapstructure:"allowed_origins"`
}

type RateLimitConfig struct {
	// 普通用户每分钟允许的 LLM 端点请求数；<=0 表示关闭限流
	LLMPerMinute int `mapstructure:"llm_per_minute"`
	// 管理员每分钟限额（通常更宽松）；<=0 时回退用 LLMPerMinute
	LLMPerMinuteAdmin int `mapstructure:"llm_per_minute_admin"`
	// 登录接口每分钟每 IP 允许的请求数（防爆破）；<=0 表示关闭
	LoginPerMinute int `mapstructure:"login_per_minute"`
}

type UploadConfig struct {
	// 单文件大小上限（MB）；<=0 表示不限制
	MaxSizeMB int `mapstructure:"max_size_mb"`
	// 文件中心允许的扩展名（小写含点，如 .pdf）；为空表示不限制
	FileExts []string `mapstructure:"file_exts"`
	// 知识库文档允许的扩展名；为空表示不限制
	DocExts []string `mapstructure:"doc_exts"`
}

type ServerConfig struct {
	Port int    `mapstructure:"port"`
	Mode string `mapstructure:"mode"`
}

type DatabaseConfig struct {
	DSN             string `mapstructure:"dsn"`
	MaxOpenConns    int    `mapstructure:"max_open_conns"`
	MaxIdleConns    int    `mapstructure:"max_idle_conns"`
	ConnMaxLifetime int    `mapstructure:"conn_max_lifetime"`
}

type RedisConfig struct {
	Addr     string `mapstructure:"addr"`
	Password string `mapstructure:"password"`
	DB       int    `mapstructure:"db"`
}

type JWTConfig struct {
	Secret string `mapstructure:"secret"`
	Expire int64  `mapstructure:"expire"`
}

type FastAPIConfig struct {
	BaseURL string `mapstructure:"base_url"`
	Timeout int    `mapstructure:"timeout"`
}

type MinIOConfig struct {
	Endpoint  string `mapstructure:"endpoint"`
	AccessKey string `mapstructure:"access_key"`
	SecretKey string `mapstructure:"secret_key"`
	Bucket    string `mapstructure:"bucket"`
	UseSSL    bool   `mapstructure:"use_ssl"`
}

type LogConfig struct {
	Level      string `mapstructure:"level"`
	Filename   string `mapstructure:"filename"`
	MaxSize    int    `mapstructure:"max_size"`
	MaxBackups int    `mapstructure:"max_backups"`
	MaxAge     int    `mapstructure:"max_age"`
	Compress   bool   `mapstructure:"compress"`
}

var Global *Config

func Load(cfgFile string) error {
	if cfgFile != "" {
		viper.SetConfigFile(cfgFile)
	} else {
		viper.SetConfigName("config")
		viper.SetConfigType("yaml")
		viper.AddConfigPath(".")
		viper.AddConfigPath("./config")
	}

	// 支持环境变量覆盖，如 APP_SERVER_PORT=9090
	viper.SetEnvPrefix("APP")
	viper.SetEnvKeyReplacer(strings.NewReplacer(".", "_"))
	viper.AutomaticEnv()

	if err := viper.ReadInConfig(); err != nil {
		return fmt.Errorf("read config: %w", err)
	}

	Global = &Config{}
	if err := viper.Unmarshal(Global); err != nil {
		return fmt.Errorf("unmarshal config: %w", err)
	}
	return nil
}
