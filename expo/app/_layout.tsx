import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";
import { configureAudio } from "../src/player";

export default function RootLayout() {
  useEffect(() => {
    configureAudio().catch(() => {});
  }, []);

  return (
    <>
      <StatusBar style="light" />
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: "#090909" } }}>
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="player/[id]" options={{ presentation: "fullScreenModal" }} />
      </Stack>
    </>
  );
}
