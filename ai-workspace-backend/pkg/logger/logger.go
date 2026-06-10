package logger

import (
	"os"

	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
	"gopkg.in/natefinch/lumberjack.v2"

	"github.com/aiworkspace/backend/internal/config"
)

var Log *zap.Logger

func Init(cfg config.LogConfig) error {
	level := zapcore.InfoLevel
	if err := level.UnmarshalText([]byte(cfg.Level)); err != nil {
		level = zapcore.InfoLevel
	}

	encoder := zapcore.NewJSONEncoder(zapcore.EncoderConfig{
		TimeKey:        "time",
		LevelKey:       "level",
		NameKey:        "logger",
		CallerKey:      "caller",
		MessageKey:     "msg",
		StacktraceKey:  "stacktrace",
		LineEnding:     zapcore.DefaultLineEnding,
		EncodeLevel:    zapcore.LowercaseLevelEncoder,
		EncodeTime:     zapcore.ISO8601TimeEncoder,
		EncodeDuration: zapcore.SecondsDurationEncoder,
		EncodeCaller:   zapcore.ShortCallerEncoder,
	})

	// 同时写文件和控制台
	fileSyncer := zapcore.AddSync(&lumberjack.Logger{
		Filename:   cfg.Filename,
		MaxSize:    cfg.MaxSize,
		MaxBackups: cfg.MaxBackups,
		MaxAge:     cfg.MaxAge,
		Compress:   cfg.Compress,
	})
	consoleSyncer := zapcore.AddSync(os.Stdout)
	core := zapcore.NewTee(
		zapcore.NewCore(encoder, fileSyncer, level),
		zapcore.NewCore(encoder, consoleSyncer, level),
	)

	Log = zap.New(core, zap.AddCaller(), zap.AddStacktrace(zapcore.ErrorLevel))
	zap.ReplaceGlobals(Log)
	return nil
}

func Sync() {
	if Log != nil {
		_ = Log.Sync()
	}
}
